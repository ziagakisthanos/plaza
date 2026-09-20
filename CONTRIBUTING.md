# Contributing

All work happens on `main`, in small commits. Each commit is checked automatically by Jenkins and reviewed by a code owner before it counts as approved.

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

## Automatic checks

Jenkins polls `main` every two minutes, and also runs every night. Each run:

1. builds all modules and runs the unit tests,
2. sends the code and the JaCoCo coverage report to SonarQube and waits for the quality gate,
3. builds the images and deploys, only if everything before it passed.

The quality gate fails the build when the project has any bug, any vulnerability or any unreviewed security hotspot, or when the new code has issues, less than 80% coverage, more than 3% duplication or unreviewed hotspots. A failed gate stops the pipeline before anything is built or deployed, and the failure notification is written to the build log.

## Review and approval

The people who review are listed in `.gitea/CODEOWNERS`.

1. Push your commit to `main` and wait for the Jenkins build.
2. A red build must be fixed straight away with a follow-up commit.
3. A code owner reviews the commit: read it with `git show <commit>` or on its Gitea page, and check the new issues SonarQube reports for it.
4. If the reviewer finds a problem, it is fixed in a follow-up commit and reviewed again.
5. If the commit is good and its build is green, the reviewer records the approval as a git note:

```
git notes --ref=reviews add -m "approved by <name>" <commit>
git push origin refs/notes/reviews
```

Approvals are listed with `git log --show-notes=reviews` after `git fetch origin refs/notes/reviews:refs/notes/reviews`.
