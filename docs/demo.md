# Demo

A walkthrough of TestPulse in about five minutes, with what to show and what it demonstrates. It doubles as the script
for a screen recording.

## Prepare

```bash
docker compose down -v                                # start from empty databases
GITHUB_TOKEN=$(gh auth token) docker compose up --build
```

Wait until `seed` has printed `created` for every run (about a minute after the services are healthy). Open:

- the dashboard: http://localhost:3000
- Mailpit: http://localhost:8025
- optional, `docker compose --profile tools up kafka-ui`: http://localhost:8090

## 1. The problem (30 s)

A flaky test passes and fails on the same code. Every false failure costs a re-run and a bit of trust, until red builds
are ignored. TestPulse finds these tests from the JUnit XML reports CI already produces.

## 2. Repositories (30 s)

Dashboard home. acme/payments and acme/checkout come first: they have flaky tests. YagmurPazi/testpulse is this
repository's own CI, imported from GitHub Actions artifacts.

*Shows:* the dashboard ranks by urgency; the importer works without touching any workflow.

## 3. A flaky test by flip rate (60 s)

acme/payments → filter **Flaky** → `refundsPartially`.

- The result strip: pass and fail alternate across commits.
- Status history: Stable → Suspicious → Flaky, each with its reason. Two observations are needed before flaky.

*Shows:* the flip-rate strategy and the state machine (ADR-0007); every status change is explained.

## 4. Broken is not flaky (30 s)

Back to acme/payments → `capturesPayment`: four failures in a row, then fixed. It is still **stable**.

*Shows:* a regression flips twice; the detector needs three flips, so a fixed bug is not called flaky.

## 5. Recovery (30 s)

`reconcilesLedger`: Stable → Suspicious → Flaky → Stable. The last change says it passed cleanly ten times in a row.

*Shows:* evidence counts only after the last status change, so a fixed test recovers instead of oscillating.

## 6. Quarantine (60 s)

acme/checkout → `appliesDiscount` (flaky: passed and failed on the same commit) → **Quarantine…** → read the
explanation → **Quarantine test**. Then **Release…**: it goes back to suspicious, not to stable.

Switch to Mailpit: the e-mails for "Flaky test" and "Quarantined" are there, with the reason and a link back.

*Shows:* manual decisions in the lifecycle, the outbox → Kafka → notification-service path, idempotent delivery.

## 7. Under the hood (60 s)

- Kafka UI: topics `testpulse.test-runs` and `testpulse.test-status-changes`, messages keyed by repository.
- `docs/architecture.md`: the container diagram and the failure table.
- GitHub Actions: build, mutation testing, dashboard, images, end-to-end; the JUnit reports uploaded for dogfooding.

*Shows:* event-driven services, database per service, quality gates (coverage, mutation score, accessibility).
