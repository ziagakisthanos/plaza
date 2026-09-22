# Nexus: artifact management for buy-02

buy-02 is built and deployed the same way as before. What changed is that every build on
`main` now also publishes its own output — jars and Docker images — to a private Nexus
Repository Manager instance, instead of that output only existing inside the build that
produced it. This document explains how, and how to check it yourself.

## Setup

Nexus Repository Manager 3.95.4 (Community Edition) runs as a Docker container, defined in
`docker-compose.nexus.yml`:

```bash
cp .env.example .env            # set NEXUS_ADMIN_PASSWORD, NEXUS_USER, NEXUS_PASSWORD
docker compose -f docker-compose.nexus.yml up -d
```

- UI and REST API: http://localhost:8091
- Docker registry: `localhost:5000`
- Data persists in the `nexus-data` volume.
- The container joins `safe-zone-net`, the same network as Jenkins and SonarQube, so Jenkins
  can reach it directly as `http://nexus:8081`.
- The heap is capped (`INSTALL4J_ADD_VM_PARAMS`, `-Xms1g -Xmx2g`) so it coexists with Jenkins
  and SonarQube on the same machine.

On first start, sign in as `admin` (initial password at `/nexus-data/admin.password` inside
the container) and accept the Community Edition end user license agreement — the REST API
refuses to configure anything until that is done.

**Screenshot:** `docs/img/nexus-dashboard.png` — the Nexus dashboard after first sign-in.

### Runs as a non-root user

The official image runs the Nexus process as its built-in `nexus` user (uid 200), never root:

```bash
$ docker exec nexus id
uid=200(nexus) gid=200(nexus) groups=200(nexus)
```

`docker top nexus` shows the same: the `java` process belongs to user `200`, not `root`.

## Repositories

`scripts/nexus-setup.sh` configures everything through the Nexus REST API — no manual UI
steps, and it can be re-run safely (it checks what already exists before creating anything):

```bash
sh scripts/nexus-setup.sh
```

| Repository | Type | Format | Purpose |
|---|---|---|---|
| `jars-releases` | hosted, write-once | maven2 | Release versions of every module's jar |
| `jars-snapshots` | hosted, redeployable | maven2 | `-SNAPSHOT` builds from `main` |
| `wars-releases` / `wars-snapshots` | hosted | maven2 | Ready for WAR packaging; unused, since buy-02 only produces jars |
| `maven-central` | proxy | maven2 | Caching mirror of Maven Central |
| `maven-public` | group | maven2 | Single URL combining `maven-central`, `jars-releases`, `jars-snapshots` |
| `docker-hosted` | hosted | docker | The 7 service/frontend images, port 5000 |

**Screenshot:** `docs/img/nexus-repositories.png` — the Browse page listing all repositories.

## Maven: publishing and dependency resolution

`ci/settings.xml` mirrors every Maven request through `maven-public`, with credentials from
the `NEXUS_USER` / `NEXUS_PASSWORD` environment variables (never hardcoded):

```bash
export NEXUS_URL=http://localhost:8091 NEXUS_USER=admin NEXUS_PASSWORD=...
./mvnw -s ci/settings.xml clean verify   # dependencies resolve through Nexus, not Maven Central directly
```

The root `pom.xml` adds `distributionManagement`, pointing at `jars-releases` and
`jars-snapshots` through a `nexus.url` property (overridable with `-Dnexus.url=...`, which is
how the Jenkins pipeline points it at the container-internal `http://nexus:8081`):

```bash
./mvnw -s ci/settings.xml deploy -DskipTests
```

Each of the 8 modules (plus the parent POM) is published. `common-security` and
`common-web`, used internally by four other services, are referenced as
`${project.version}` rather than a hardcoded version, so a version bump never leaves a
service pointing at a stale internal dependency.

**Screenshot:** `docs/img/nexus-jars-snapshots.png` — `jars-snapshots` browsed in the UI,
showing the published modules.

## Docker images

`scripts/publish-images.sh` builds all 7 images (`discovery`, `gateway`, `user-service`,
`product-service`, `media-service`, `order-service`, `frontend`) and pushes them to
`docker-hosted` as `localhost:5000/buy-02/<name>:<version>`:

```bash
sh scripts/publish-images.sh
```

```bash
docker login localhost:5000 -u admin
docker pull localhost:5000/buy-02/order-service:1.1.0
```

**Screenshot:** `docs/img/nexus-docker-hosted.png` — the `docker-hosted` repository browsed
in the UI, showing the pushed images.

## Continuous integration

The Jenkins pipeline (`Jenkinsfile`) adds two stages, `main` only, after the build, tests and
SonarQube quality gate:

| Stage | What it does |
|---|---|
| Publish Artifacts | `mvn -s ci/settings.xml -Dnexus.url=http://nexus:8081 deploy -DskipTests` |
| Publish Images | `sh scripts/publish-images.sh` |

Both use a Jenkins credential named `nexus-credentials` (username/password), injected only
for those two steps via `withCredentials`. Every push to `main` therefore builds, tests, and
publishes automatically — no manual step.

**Screenshot:** `docs/img/jenkins-pipeline-stages.png` — a Jenkins build showing all stages
green, including Publish Artifacts and Publish Images.

## Versioning

`main` carries a SemVer `-SNAPSHOT` version (currently `1.2.0-SNAPSHOT`). Every build on
`main` publishes that snapshot to `jars-snapshots`, which Nexus timestamps automatically so
successive snapshot deploys never collide.

Cutting a release is two commits:

```bash
./mvnw versions:set -DnewVersion=1.1.0 -DgenerateBackupPoms=false   # drop -SNAPSHOT
git commit -am "release 1.1.0" && git tag v1.1.0
./mvnw -s ci/settings.xml deploy -DskipTests                        # goes to jars-releases
sh scripts/publish-images.sh                                        # goes to docker-hosted, tag 1.1.0

./mvnw versions:set -DnewVersion=1.2.0-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "start 1.2.0 snapshot"
```

Maven picks the release or snapshot repository on its own, based on whether the version ends
in `-SNAPSHOT` — nothing else changes between the two commands above.

`jars-releases` is write-once: redeploying an already-published release version is rejected.

```
$ ./mvnw -s ci/settings.xml deploy -DskipTests   # run a second time against the same version
[ERROR] ... status code: 409, reason phrase: jars-releases/... cannot be updated as asset
already exists and redeploy is not allowed (409)
```

Two real releases exist, `1.0.0` and `1.1.0` (tags `v1.0.0`, `v1.1.0`), and both are
independently retrievable at any time:

```bash
curl -u admin:$NEXUS_PASSWORD -O http://localhost:8091/repository/jars-releases/platform/zone01/common-web/1.0.0/common-web-1.0.0.jar
curl -u admin:$NEXUS_PASSWORD -O http://localhost:8091/repository/jars-releases/platform/zone01/common-web/1.1.0/common-web-1.1.0.jar

docker rmi localhost:5000/buy-02/order-service:1.0.0
docker pull localhost:5000/buy-02/order-service:1.0.0   # comes back, unchanged
docker pull localhost:5000/buy-02/order-service:1.1.0   # a different image, same registry
```

**Screenshot:** `docs/img/nexus-jars-releases.png` — `jars-releases` in the UI, showing both
`1.0.0` and `1.1.0` present side by side.
