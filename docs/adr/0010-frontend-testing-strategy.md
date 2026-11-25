# 0010. Frontend testing strategy

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

The dashboard is where TestPulse's results reach people, so a broken page hides correct analysis. At the same time,
TestPulse is about flaky tests: its own tests must not be flaky, and must not hide flakiness either.

Which tests does the dashboard have, at which level, and against what?

## Considered options

Component and page tests:

1. Mock the hooks or the API module in each test
2. Mock the network with Mock Service Worker (MSW), and render the real route tree

End-to-end tests:

- A. Against the dashboard with a mocked API (Playwright route interception)
- B. Against the real stack (`docker compose`), seeded once with fixed data
- C. Against the real stack, with each test uploading its own data

## Decision outcome

Chosen options: **2, MSW and the real route tree**, and **C, the real stack with data per test.**

### Levels

| Level | Tool | What it proves | Runs |
|---|---|---|---|
| Unit | Vitest | Formatting, ordering, gap filling, URL parsing, retry rules | every build |
| Page | Vitest, Testing Library, MSW | Each page renders the API's responses, handles errors, 404 and 409, and changes status | every build |
| Accessibility | axe-core in jsdom | No WCAG 2.2 A/AA violation on any page (except contrast, which jsdom cannot compute) | every build |
| End to end | Playwright, axe in Chromium | Upload → outbox → Kafka → analysis → dashboard works, quarantine is stored, contrast holds | CI, after the images are built |

### Page tests

- Tests render the **whole app at a URL** (`renderApp('/tests/…')`) with the real router and a fresh query cache, and
  query the page by role and accessible name, as a user or a screen reader would find things.
- MSW answers at the network level, so the API client, the query hooks and the components run unchanged. Handlers
  share fixtures shaped after the demo data; a test overrides one endpoint for its case.
- A request without a handler **fails the test** instead of reaching the network or hanging.
- Time zone is pinned to UTC, so dates render the same on every machine.

### End-to-end tests

- Every test uploads the demo's flaky story under **a repository name of its own** and waits until the analysis has
  marked the test flaky. Tests do not depend on each other, on their order or on the demo seed, can run in parallel
  and can be repeated against the same stack.
- Waiting uses polling with a timeout (`expect.poll`), never fixed sleeps.
- **No retries.** A test that passes only on a retry is flaky; retrying would hide exactly what this project exists to
  find. The JUnit reports of all levels are kept, so TestPulse can analyse its own tests (dogfooding).
- On failure, CI keeps the Playwright trace, a screenshot and the logs of every service.

Why not option 1: mocking hooks tests the components against what we think the hooks return, and misses mistakes in
the API client, the cache keys and the error handling. Why not A: it cannot find a mismatch between the dashboard's
types and the real API. Why not B: tests that change shared data (quarantine) would depend on their order.

### Consequences

- Good: page tests are fast (about a second for all) yet exercise everything except the real server.
- Good: the end-to-end tests cover the whole event-driven pipeline, which no service test covers on its own.
- Bad: the end-to-end job needs every image and takes minutes; it runs in CI, after the image builds, not on every
  local change.
- Bad: end-to-end repositories accumulate in a long-running local stack; `docker compose down -v` resets it.
