Unmodified JUnit XML reports produced by this repository's own CI, used as realistic parser fixtures:

| File | Writer | Source |
|---|---|---|
| `surefire-architecture-rules.xml` | Maven Surefire | run 45619835962, artifact `junit-reports` |
| `failsafe-ingestion-it.xml` | Maven Failsafe | run 45619835962, artifact `junit-reports` |
| `vitest-dashboard.xml` | Vitest | run 45699904602, artifact `junit-reports-dashboard` |
| `playwright-e2e-passed.xml` | Playwright | run 45699904602, artifact `junit-reports-e2e` |
| `playwright-e2e-failed.xml` | Playwright | run 45689500171, artifact `junit-reports-e2e` (a real failure) |

`../samples/vitest-failures-and-skips.xml` was written by Vitest locally for a throwaway test file with a failed
assertion, a thrown exception, a skipped and a todo test; only the host name and the file path were changed.
