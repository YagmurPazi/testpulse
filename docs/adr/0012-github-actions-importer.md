# 0012. Importing CI runs from GitHub Actions

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

Until now, test reports reached TestPulse only through uploads: a CI step had to call `POST /api/v1/test-runs`.
That needs a change in every pipeline and a TestPulse instance the CI can reach. TestPulse's own CI already keeps its
JUnit XML reports as artifacts (`junit-reports*`), for 90 days, to analyse its own tests (dogfooding).

How does TestPulse get CI results without changing the pipelines?

## Considered options

1. A step in every workflow that uploads the reports (the existing API)
2. GitHub webhooks (`workflow_run` events) that notify TestPulse when a run completes
3. Polling GitHub's REST API for completed runs and downloading their artifacts

## Decision outcome

Chosen option: **3, polling**, next to the upload API, which stays.

- Every 5 minutes ingestion-service lists the 20 most recent completed runs of each configured repository and
  imports those not ingested yet, oldest first.
- A run is imported through the **same use case as an upload**: an imported run is identical to an uploaded one, and
  its identity (repository, run id, attempt) makes it idempotent. A run uploaded and later imported is ingested once,
  and a run already ingested is skipped before anything is downloaded.
- All artifacts whose names start with `junit-reports` form one run (services, dashboard, end-to-end). If a re-run
  uploaded an artifact of the same name again, the newest one counts.
- A re-run of a workflow is a new attempt and a new run, which feeds the same-commit strategy of
  [ADR-0007](0007-flakiness-detection-and-test-status.md).

Why not webhooks: GitHub must reach TestPulse over the internet, which a local `docker compose up` cannot offer, and
a missed delivery would need polling as a fallback anyway. Polling is simpler and works everywhere; a 5 minute delay
does not matter for flakiness, which is a trend over many runs. Why not only uploads: every repository would have to
change its workflows before TestPulse can show anything.

### Security

- Downloading artifacts needs a token, even for public repositories. The importer exists only when one is configured
  (`GITHUB_TOKEN`); without it, TestPulse never calls GitHub.
- GitHub answers a download with a redirect to a storage host. The redirect is followed by hand, **without the
  token**, so the token never leaves GitHub's API.
- Archives come from the network and are limited to 50 MB, compressed and after decompression (ZIP bombs).

### Consequences

- Good: `GITHUB_TOKEN=$(gh auth token) docker compose up` analyses this repository's own CI history with no change to
  any pipeline.
- Good: a broken run is counted and retried on the next import without stopping the others.
- Bad: artifacts expire after at most 90 days, so history before that is lost (as noted in the README).
- Bad: only the 20 most recent runs are looked at per import. A repository with more than 20 runs per 5 minutes needs
  a larger window or webhooks.
- Bad: earlier attempts of a run that was re-run before the importer saw it are not imported; the runs API reports
  only the latest attempt.
