# Contributing

Every change is built and tested by Jenkins, and every change on `main` also has to pass the SonarQube quality gate before anything is deployed. Small changes can go straight to `main`. A change that someone should read first goes on a branch and through a pull request.

## Commit messages

Short, lowercase, imperative, one change per commit:

```
add user auth
fix sonar bugs
```

## Before you commit

- Backend: `./mvnw clean verify` on JDK 21 must pass.
- Frontend: `cd frontend && npm test` must pass.
- Never commit `.env`, tokens or passwords.

## What Jenkins does

Jenkins polls the repository every two minutes and also runs every night. The stages depend on the branch that is being built.

| Stage | Any branch | `main` only |
|---|---|---|
| Build all modules and run the unit tests (with JaCoCo coverage) | yes | yes |
| SonarQube analysis and quality gate | no | yes |
| Build the images | no | yes |
| Deploy, smoke check and rollback if it fails | no | yes |

A branch build therefore never deploys and never touches the SonarQube project. SonarQube Community Build analyses one branch only, so the quality gate runs once the change is on `main`.

The gate fails the build when the new code has any issue, less than 80% coverage, more than 3% duplication or unreviewed security hotspots, or when the project has any bug or vulnerability. A failed gate stops the pipeline before any image is built or deployed. What has been fixed because of the gate is listed in [docs/sonarqube.md](docs/sonarqube.md).

## Working on a branch and a pull request

1. Create a branch named `feature/<what-it-does>` and push it. Jenkins builds and tests it.
2. Open a pull request into `main` on Gitea.
3. The code owner listed in `.gitea/CODEOWNERS` reviews it. Problems are fixed with follow-up commits on the same branch.
4. Merge once the branch build is green and the review is done.
5. Watch the build of `main` that follows: the analysis and the deploy run there. If the gate fails, fix it straight away, with a small commit or a new branch.

## Working directly on `main`

For a small change, commit on `main` and wait for the Jenkins build. A red build is fixed straight away with a follow-up commit.
