# Testing strategy

TestPulse detects flaky tests, so its own tests have two jobs: prove the system works, and never become the problem
they are meant to find. This page describes how the tests are organised across all services and the dashboard, the
quality gates that enforce them, and the rules that keep them deterministic.

## The levels

| Level | Tools | What it proves | Where |
|---|---|---|---|
| Domain unit tests | JUnit 5, AssertJ, parameterised tables | Detection strategies, the status state machine (every cell of the transition table), notification policy | `*/domain/*Test` |
| Application unit tests | In-memory port implementations, no mocks of our own code | Use cases: idempotency, per-channel delivery, import order, error paths | `*/application/service/*Test` |
| Mutation tests | PIT | The unit tests notice when the domain or application code is changed | CI job `Mutation testing` |
| Architecture tests | ArchUnit, shared rules | Hexagonal layering, framework-free core, independent adapters | `ArchitectureTest` per service |
| Contract tests | Golden JSON files | Event JSON stays compatible (field names, types, schema version) | `event-contracts` |
| Adapter tests | MockMvc, `MockRestServiceServer`, Mockito for framework types | HTTP mapping and problem details, Slack payload, GitHub client (redirect without token, size limits) | `*/adapter/**/*Test` |
| Integration tests | Testcontainers: PostgreSQL 18, Kafka 4.4, Mailpit | SQL, transactions, row locks, Flyway, consumers against a real broker, e-mail through real SMTP | `*IT` |
| Dashboard page tests | Vitest, Testing Library, MSW | Every page against the API's responses, at the network level, through the real router | `dashboard/src/**/*.test.tsx` |
| Accessibility | axe-core in jsdom, axe in Chromium, a color token test | WCAG 2.2 A/AA on every page; 4.5:1 contrast of every text color in both themes | page tests, `dashboard/tokens`, end to end |
| End to end | Playwright against the compose stack | Upload → Kafka → analysis → dashboard → e-mail, including quarantine and release | `dashboard/e2e` |
| Demo replay | JUnit | The demo data still tells the stories the README promises | `DemoRunsTest` |

The pyramid is deliberately wide at the bottom: most behaviour is in plain Java and tested in milliseconds; the slower
levels test what only they can test (SQL, Kafka, browsers), not the same rules again.

## Quality gates

Every pull request runs all levels in [GitHub Actions](../.github/workflows/ci.yml). A gate that fails breaks the
build.

| Gate | Threshold | Why this value |
|---|---|---|
| Checkstyle, ESLint, Prettier | no violation | formatting is not a review topic |
| SpotBugs | no medium or high finding | |
| TypeScript | strict, `noUncheckedIndexedAccess`, `exactOptionalPropertyTypes` | |
| JaCoCo line / branch coverage | services 95 % / 90 % | measured 98–99 % / 93–98 % (unit and integration tests together) |
| | outbox 80 % / 75 %, event contracts 85 % / 70 % | less logic, measured 86 % / 79 % and 92 % / 75 % |
| Vitest coverage | lines 92 %, branches 85 % | measured 94.6 % / 89.6 % |
| PIT mutation score | 90 % | measured 100 % in all three services |
| ArchUnit | every rule, no empty layers | a rule over an empty package would pass while checking nothing |

Every threshold sits a little below the measured value. It catches code that arrives without tests, without failing
on every refactoring that moves a line. Coverage alone says little (a test without assertions covers code); the
mutation score says whether the tests would notice a change.

## Keeping our own tests deterministic

Each rule removes one common cause of flaky tests.

| Cause | Rule |
|---|---|
| Shared state between tests | Integration tests empty their tables before each test; end-to-end tests upload their data under a repository name of their own |
| Waiting with fixed sleeps | Asynchronous results are awaited with a timeout: Awaitility in Java, `expect.poll` and auto-waiting locators in Playwright |
| Test order | No test depends on another; end-to-end tests run in parallel |
| Clock and time zone | Services take a `Clock`; dashboard tests pin the time zone to UTC |
| Kafka ordering | Events of one repository share a partition; a test waits for a later *marker* event instead of guessing when an earlier one was handled |
| Real network | MSW fails a dashboard test on any request without a handler; the GitHub importer is off in integration tests |
| Environment differences | Containers use the same image versions as `compose.yaml` |
| Hiding flakiness | **No retries** in Playwright or Surefire: a test that passes only on a retry is flaky and must be seen |

## Dogfooding

CI uploads the JUnit XML of every level as artifacts (`junit-reports`, `junit-reports-dashboard`, `junit-reports-e2e`),
also for failed runs. With a GitHub token, TestPulse imports them ([ADR-0012](adr/0012-github-actions-importer.md)) and
analyses its own tests: the first flaky test it would have found is the Testcontainers start-up timeout that once
failed a build here and passed on the re-run.

## Running the tests

```bash
./mvnw verify                                   # everything, as in CI (needs Docker)
./mvnw verify -DskipITs -Djacoco.skip           # without Docker: unit, architecture, contract tests
./mvnw -pl services/analysis-service -am test-compile org.pitest:pitest-maven:mutationCoverage
cd dashboard && npm test                        # page, accessibility and token tests
cd dashboard && npm run e2e                     # against a running `docker compose up`
```
