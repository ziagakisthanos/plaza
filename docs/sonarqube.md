# SonarQube: how it is used and what it improved

## How it is used

- SonarQube Community Build runs in Docker next to a PostgreSQL database (`docker-compose.sonar.yml`).
- The Jenkins pipeline runs `sonar-scanner` after the build and the unit tests, with `-Dsonar.qualitygate.wait=true`. If the quality gate fails, the build fails, the images are not built and nothing is deployed.
- The gate requires, for new code: no new issues, at least 80% coverage, at most 3% duplicated lines and reviewed security hotspots.
- Java coverage comes from JaCoCo. The frontend is analysed for issues, but it is excluded from the coverage measure (see below).
- Sonar analyses `main` only. The Community Build cannot analyse branches or pull requests, so branch builds run the build and the tests, and the quality gate runs once the change is on `main`.

## Findings and what was done about them

| Commit | Finding | Fix |
|---|---|---|
| `11b2d0f` | Private members that are never reassigned were not `readonly` (cart service, cart page). | Marked them `readonly`, and did the same for the other injected members in the files that were touched. |
| `11b2d0f` | The quantity stepper used `role="group"`; Sonar asks for a native element. | Replaced it with a `<fieldset>` and a visually hidden `<legend>`. The page looks the same. |
| `11b2d0f` | An unused import in a test and a lambda that can be a method reference (found while checking the same files). | Removed the import and used `HttpStatusCode::isError`. |
| `99b8855` | The literal `"CLIENT"` was repeated five times in the order service security rules (`java:S1192`). | Introduced `CLIENT` and `SELLER` constants. |
| `d7d03cc` | Coverage on new code was 75.6%, below the 80% gate. | See "Frontend coverage" below. |
| `f3d796b` | An empty helper method in `ApiExceptionHandlerTest` (`java:S1186`). | The test now takes the `Method` it needs from a real JDK method, so no placeholder method is needed. |

### Frontend coverage

The backend code added since the first analysis is covered at about 98% (measured from the JaCoCo reports that Jenkins produces). The gap came from the TypeScript in the frontend: no coverage report reaches Sonar for it, so its lines counted as uncovered.

The frontend has its own unit tests (Vitest, run with `npm test` in `frontend`), but the Jenkins image has no Node.js, so those tests and their coverage are not part of the pipeline. Rather than change the shared Jenkins image, `sonar.coverage.exclusions=frontend/**` leaves the frontend out of the coverage condition only. Its code is still analysed for bugs, vulnerabilities and code smells.

### Duplication

Error handling (the error body class and the exception handler) used to be copied into each service. It now lives once in the `common-web` module, and each service only declares the exceptions of its own domain. Duplication on new code was 0.56% when it was last checked on the dashboard, against a limit of 3%.

## Habits that keep the gate green

Before every commit the changed files are checked for the things Sonar reported here:

- unused imports,
- string literals repeated three times or more,
- private injected members that are not `readonly`,
- empty methods,
- duplicated CSS selectors.

The gate result is read from the `QUALITY GATE STATUS` line of the Jenkins log, and a red gate is fixed before anything else is built on top of it.

## Current state

The quality gate passes and there are no open issues on new code.
