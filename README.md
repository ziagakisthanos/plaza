## buy-01 E-Commerce Microservices Platform


A full-stack e-commerce platform built with **Spring Boot microservices** (backend) and
**Angular** (frontend). Users register as clients (browse) or sellers (manage products and
images). Demonstrates service decomposition, synchronous and asynchronous inter-service
communication, JWT-based security with role and ownership enforcement, secure file uploads,
and object storage.

---

## Architecture

```
                         ┌─────────────┐
   Angular SPA  ──HTTPS──▶│   Gateway   │  (single entry point, JWT-aware, CORS, routing)
   (localhost:4200)       │  :8443/:8080│
                          └──────┬──────┘
                                 │  routes by service name via Eureka
          ┌──────────────────────┼──────────────────────┐
          ▼                      ▼                       ▼
   ┌─────────────┐        ┌─────────────┐         ┌─────────────┐
   │ User Service│        │Product Svc  │         │ Media Service│
   │   :8081     │        │   :8082     │         │    :8083     │
   │ auth, JWT,  │        │ CRUD, owner-│         │ upload/serve │
   │ profiles    │        │ ship checks │         │ validation   │
   └──────┬──────┘        └──────┬──────┘         └──────┬───────┘
          │ userdb               │ productdb             │ mediadb + MinIO
          ▼                      ▼                       ▼
   ┌───────────────────────────────────────────────────────────┐
   │              MongoDB (database per service)                │
   └───────────────────────────────────────────────────────────┘

   Product ──"PRODUCT_DELETED" event──▶ Kafka ──▶ Media (cleans up orphaned images)

   Discovery (Eureka, :8761) — service registry all services register with
```

### Services
- **Discovery (Eureka, 8761)** — service registry; services find each other by name, not address.
- **Gateway (8080 / 8443 HTTPS)** — single front door; routes by service name, terminates TLS,
  applies CORS.
- **User Service (8081)** — registration, login, JWT issuance, profiles (`/me`), roles.
- **Product Service (8082)** — product CRUD; public reads, seller-only writes, ownership checks.
- **Media Service (8083)** — image upload/serve/delete; MIME + size validation; MinIO storage.
- **common-security** — shared library (`JwtService`, `JwtAuthFilter`) imported by all services.

---

## Tech Stack

**Backend:** Java 21, Spring Boot 3.3, Spring Cloud (Eureka, Gateway), Spring Security,
Spring Data MongoDB, Spring Kafka, jjwt, MinIO client, Maven (multi-module).

**Frontend:** Angular 21 (standalone components), Angular Material, RxJS, TypeScript.

**Infrastructure:** MongoDB, MinIO (S3-compatible object storage), Apache Kafka (KRaft),
all via Docker.

---

## Key Design Decisions

- **Database per service** — each service owns its own MongoDB database; the boundary is
  enforced by giving each service only its own connection string. No service reads another's data.
- **Object storage for images** — image *bytes* live in MinIO; only *metadata* lives in Mongo.
  Databases aren't for blobs, and a shared object store works across multiple stateless instances.
- **Stateless JWT auth** — the server keeps no session; each request carries a signed token
  re-verified with a shared secret. Any service instance can verify any token. Trade-off:
  no instant revocation, mitigated with short (1h) expiry.
- **Self-securing services (zero-trust)** — each service validates the JWT itself via the shared
  `common-security` library, rather than trusting the gateway. A service is secure regardless of
  how a request reaches it.
- **Identity from the token, never the body** — ownership (`userId`) is always taken from the
  authenticated caller's token, never accepted from the request, preventing impersonation.
- **Sync vs async communication** — reads, auth, and uploads are synchronous (the caller needs a
  response). Product deletion publishes an async `PRODUCT_DELETED` event so Media can clean up
  orphaned images without the user waiting and without coupling the two services.
- **Secure uploads** — image type is verified by **sniffing file content** (magic bytes), not the
  client-supplied content-type; size capped at 2 MB at both framework and application level.

---

## Prerequisites

- Java 21, Maven
- Node.js 20+ and Angular CLI (`npm install -g @angular/cli`)
- Docker + Docker Compose

---

## Running the Project

### 1. Start infrastructure (MongoDB, MinIO, Kafka)
From the project root:
```bash
docker compose up -d
```
Verify: `docker ps` shows `buy01-mongo`, `buy01-minio`, `buy01-kafka`.

- MinIO console: http://localhost:9001 (user/pass: `minioadmin` / `minioadmin`)
- The `product-images` bucket is auto-created by Media Service on startup.

### 2. Set the JWT secret
Each service reads `JWT_SECRET` (must be identical across services). Set it as an environment
variable, or rely on the dev default in each service's `application.yml`. When you start the stack
with `docker compose`, put it in `.env` (copy `.env.example`), which compose reads automatically.

### 3. Start the backend services (in order)
Run from your IDE or with Maven, starting Discovery first:
1. `discovery`   (8761)
2. `gateway`     (8443 HTTPS)
3. `user-service`, `product-service`, `media-service`

Confirm all services register at the Eureka dashboard: http://localhost:8761

### 4. Start the frontend
```bash
cd frontend
npm install
ng serve --ssl
```
Open **https://localhost:4200**.

> **Self-signed certificates:** both the frontend and gateway use self-signed certs, so the
> browser will warn that the connection is not trusted. Accept both (visit
> `https://localhost:8443/products` once directly to trust the gateway cert), or enable
> `chrome://flags/#allow-insecure-localhost`. In production these would be CA-issued certs.

---

## HTTPS / TLS

TLS terminates at the **gateway** (self-signed cert via a PKCS12 keystore); the frontend is
also served over HTTPS. Internal service-to-service traffic is HTTP over the trusted network —
the standard edge-termination pattern. Regenerate the gateway keystore with:
```bash
keytool -genkeypair -alias buy01 -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore keystore.p12 -validity 365 -dname "CN=localhost" -storepass changeit
```

---

## API Overview

All requests go through the gateway (`https://localhost:8443`).

| Method | Endpoint | Access | Description |
|--------|----------|--------|-------------|
| POST | `/auth/register` | Public | Register (role: CLIENT or SELLER) |
| POST | `/auth/login` | Public | Log in → returns JWT |
| GET  | `/users/me` | Authenticated | Current user's profile |
| PUT  | `/users/me` | Authenticated | Update name / avatar |
| GET  | `/products` | Public | List all products |
| GET  | `/products/{id}` | Public | Get one product |
| POST | `/products` | SELLER | Create a product |
| PUT  | `/products/{id}` | SELLER (owner) | Update own product |
| DELETE | `/products/{id}` | SELLER (owner) | Delete own product |
| POST | `/media/images` | SELLER | Upload an image (multipart, ≤2MB, image/*) |
| GET  | `/media/images/{id}` | Public | Serve an image |
| GET  | `/media/product/{productId}` | Public | List a product's images |
| DELETE | `/media/images/{id}` | SELLER (owner) | Delete an image |

Every service exposes `/actuator/health`.

---

## Frontend Features

- Sign-in / sign-up with role selection (reactive forms, inline validation)
- Public product listing with images (Material card grid)
- Seller dashboard: create / edit / delete products, upload images
- Profile page with randomizable preset SVG avatars
- Route guards (auth + role) and an HTTP interceptor that attaches the JWT and handles 401/403
- **Note:** frontend guards are UX only; authorization is enforced server-side.

---

## Security Summary

- Passwords hashed with **BCrypt**; never returned in any response.
- **JWT** signed with a secret; role and user id carried as claims.
- **Role-based** access (public reads, seller-only writes) and **ownership** enforcement
  (only the creating seller edits/deletes a product or its media).
- Identical error responses for bad email vs. bad password (**no user enumeration**).
- Uploads validated by **content sniffing** and size limits.
- Consistent structured error responses (`ErrorResponseDTO`) across all services, including
  security-layer 401/403.

---

## Code Quality with SonarQube

SonarQube (Community Build, with PostgreSQL) runs in Docker and analyses the Java modules and the
Angular frontend. JaCoCo provides Java coverage, `sonar-project.properties` describes what to
analyse, and Jenkins fails the build when the quality gate fails.

### Tests
```bash
./mvnw clean verify                     # all Java modules, JDK 21; writes JaCoCo reports
cd frontend && npm ci && npm test       # Vitest; on Node 26 prefix with NODE_OPTIONS=--no-experimental-webstorage
```

### Start SonarQube
```bash
cp .env.example .env                    # then set SONAR_DB_PASSWORD
docker compose -f docker-compose.sonar.yml up -d
```
- UI: http://localhost:9100 (host port 9100, because MinIO already uses 9000).
- The host needs `vm.max_map_count >= 524288` (`sysctl vm.max_map_count`).
- Start this stack before Jenkins: it creates the `safe-zone-net` network that Jenkins joins.

### One-time SonarQube setup (web UI)
1. Log in as `admin` / `admin` and set a new password.
2. Administration > Configuration > General Settings > Security: turn on **Force user authentication**.
3. Administration > Security > Permissions: remove every global permission from **Anyone**.
4. Create the group `developers` and a user `ci-scanner` that can only run analyses. Generate a token
   for `ci-scanner` and keep it.
5. Create the project manually with key `safe-zone`, main branch `main`, new code = *Previous version*.
   Give `developers` Browse and See Source Code on it.
6. Quality Gates: copy **Sonar way** as `safe-zone gate`, set it as default and add the overall-code
   conditions: reliability issues > 0, security issues > 0, security hotspots reviewed < 100%.

### Run an analysis by hand
Put the token in `.env` as `SONAR_TOKEN=...` (`.env` is git-ignored), then:
```bash
./mvnw clean verify
set -a; . ./.env; set +a
docker run --rm --network safe-zone-net -u "$(id -u):$(id -g)" \
  -e SONAR_HOST_URL=http://sonarqube:9000 -e SONAR_TOKEN \
  -v "$PWD:/usr/src" sonarsource/sonar-scanner-cli -Dsonar.qualitygate.wait=true
```
With `sonar.qualitygate.wait=true` the scanner exits with an error when the gate fails. Results are at
http://localhost:9100/dashboard?id=safe-zone.

### Quality gate
| Scope | Fails when |
|-------|-----------|
| Whole project | any bug, any vulnerability, security hotspots reviewed < 100% |
| New code | any new issue, coverage < 80%, duplication > 3%, hotspots reviewed < 100% |

### Review process
Commit conventions, the automatic checks and how commits are reviewed and approved are in
[CONTRIBUTING.md](CONTRIBUTING.md). Reviewers are listed in `.gitea/CODEOWNERS`.

---

## CI/CD with Jenkins

Jenkins runs in Docker (`jenkins/Dockerfile`: Jenkins LTS + Docker CLI + Compose plugin + Maven +
SonarScanner CLI) and drives the pipeline defined in the root `Jenkinsfile`.

### Start Jenkins
Start SonarQube first (see above); Jenkins joins its `safe-zone-net` network.
```bash
docker compose -f docker-compose.jenkins.yml up -d --build
```
- UI: http://localhost:8090
- First boot only, unlock with:
  `docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword`
  then install the suggested plugins and create an admin user.
- Jenkins talks to the host's Docker daemon through the mounted `/var/run/docker.sock`.
  `group_add: "999"` in `docker-compose.jenkins.yml` must match the host's docker group id
  (`getent group docker`).
- All Jenkins state (jobs, users, credentials, plugins) lives in the named volume
  `safe-zone_jenkins_home`. It survives restarts and rebuilds, but `docker compose down -v` or
  `docker volume rm safe-zone_jenkins_home` wipes it.

### One-time Jenkins setup
1. **Credentials** (Manage Jenkins > Credentials > Global):
   - `jwt-secret` — *Secret text*, ID exactly `jwt-secret`, value = the `JWT_SECRET` used by the
     services. The `Jenkinsfile` reads it with `credentials('jwt-secret')`; it is never hardcoded.
   - `sonar-token` — *Secret text*, ID exactly `sonar-token`, value = the `ci-scanner` token. Read
     with `credentials('sonar-token')`.
   - A Git credential (username + password/token) for the repository.
2. **Create the job**: New Item > *Pipeline* (e.g. `safe-zone-pipeline`) > Pipeline definition
   *Pipeline script from SCM* > SCM *Git* > repository URL + the Git credential > branch
   `*/main` > Script Path `Jenkinsfile`.
3. **Run it once** with *Build Now*. This registers the polling trigger from the `Jenkinsfile`.
4. **Permissions** (Manage Jenkins > Security): anonymous access is denied. Use
   *Matrix-based security* to give an admin full rights and other users read-only, and disable
   user sign-up.

### Pipeline stages
| Stage | What it does |
|-------|--------------|
| Checkout | `checkout scm` — fetches `origin/main` |
| Build | `mvn clean package -DskipTests` for all modules |
| Test | `mvn test`; JUnit results are published and archived. A failing test stops the pipeline |
| SonarQube Analysis | `sonar-scanner -Dsonar.qualitygate.wait=true` against `http://sonarqube:9000`. A failed quality gate stops the pipeline before any image is built |
| Build Images | Tags each current `buy01-<service>` image as `:backup`, then `docker compose build` |
| Deploy | `docker compose up -d`, waits, and fails if any service is not `running` |

### Behaviour
- **Automatic trigger:** the job polls the repository every ~2 minutes (`pollSCM`); a new commit
  on `main` starts a build. No webhook is needed.
- **Continuous monitoring:** a nightly `cron` trigger (`H 2 * * *`) runs the whole pipeline, including
  the analysis, even when nothing changed.
- **Rollback:** if the pipeline fails after new images were built, the `:backup` images are
  re-tagged as `:latest`, restoring the last known-good build.
- **Cleanup:** after every run (pass or fail) the pipeline runs `docker compose down
  --remove-orphans`, so nothing is left running. Named data volumes are kept.
- **Notifications:** written to the build console log (status, job, build number, duration,
  link) for success, failure/rollback, abort, and a `DEPLOYED` event after the smoke check.
- **Test reports:** JUnit trend and per-build results in the Jenkins UI; the raw surefire reports
  are archived with each build.
- **Concurrency/retention:** one build at a time, 30-minute timeout, last 20 builds kept.

### Notes
- Stop your manual dev stack (`docker compose down`) before a pipeline run: services use fixed
  `container_name`s (`buy01-*`), which would collide with the pipeline's containers.
- Mounting `docker.sock` and running Jenkins as root gives it control of the host's Docker
  daemon. That is a deliberate trade-off for building and deploying images from the pipeline.

---

## Known Trade-offs / Future Work

- Error-handling boilerplate is duplicated across services (candidate for a `common-web` module).
- No cart/checkout — CLIENT is browse-only by design (commerce is out of scope).
- JWTs are non-revocable by design; production would add refresh tokens or a blocklist.
- Cross-store deletes (MinIO + Mongo) aren't transactional; a reconciliation sweep would handle
  rare orphaned objects in production.