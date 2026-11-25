# 0007. Flakiness detection and the test status lifecycle

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

analysis-service must decide, from a test's results over time, whether the test is flaky, and track that decision
as a status that people can rely on and act on. Two kinds of mistakes are costly:

- **False positives** erode trust: a real regression that is fixed a day later is not flaky, and neither is a test
  that keeps failing because the code is broken.
- **Oscillation** erodes trust as well: a status that flips back and forth on every run is noise.

How do we detect flakiness, and how does a test move between statuses?

## Considered options

Detection:

1. A single rule (for example "failed and passed within the last N runs")
2. Several independent strategies, combined (Strategy pattern)
3. A statistical model (for example a Bayesian failure probability per test)

Lifecycle:

- A. A status computed from scratch on every run
- B. An explicit state machine with allowed transitions (State pattern)

## Decision outcome

Chosen options: **2, independent strategies**, and **B, an explicit state machine.**

### Strategies

| Strategy | Evidence | Verdict |
|---|---|---|
| Same-commit conflict | The test passed and failed on the same commit (a CI re-run), or passed only after failed attempts within one run (Surefire reruns) | Flaky |
| Flip rate | Switches between pass and fail in the latest 20 executions, at least 5 samples | Suspicious from 0.15, flaky from 0.30 |

The detector keeps the strongest verdict. A new strategy, such as a duration anomaly, is a new class.

**Thresholds.** A fixed regression produces exactly two switches (pass, fail, ..., fail, pass); in a window of 20 that
is a rate of 0.105, and in a window of 5 it is 0.5. A flip rate alone would call it flaky. The flip rate therefore
counts only from **three switches**, and the suspicious rate is 0.15 (three switches in twenty runs). A test that
keeps failing never switches and stays stable: it is broken, not flaky. Skipped executions are ignored. All values are
configurable (`testpulse.analysis.detection`).

### Lifecycle

```
Stable ──any signal──▶ Suspicious ──flaky signal──▶ Flaky ──quarantine (manual)──▶ Quarantined
  ▲                        │                         │                                 │
  └──────recovered─────────┴────────recovered────────┘                                 │
                           ▲                                                           │
                           └──────────────────────release (manual)─────────────────────┘
```

- **Two observations before flaky.** A stable test first becomes suspicious; flaky needs new evidence after that.
- **Evidence starts fresh after every status change.** Only executions after the last change are evaluated. Without
  this, old evidence still inside the window would push a released test straight back to flaky and make a recovered
  test oscillate.
- **Recovered** means: no signal since the last change and at least 10 clean passes in a row.
- **Quarantine is a team decision**, never automatic: it means ignoring a test's result in CI. Automatic evaluations do
  not change a quarantined test; release returns it to suspicious for observation.
- Invalid manual actions (quarantining a stable test, releasing a flaky one) are rejected by the domain.
- **Event time.** Executions and changes are dated with the run's ingestion time, not the processing time, so
  replaying the event stream yields the same statuses.

Why not option 3: a statistical model would be more precise with lots of data, but it is harder to explain to the
people who must act on it, and TestPulse's value is an explanation such as "passed and failed on the same commit
9f2c4e1". Every status change carries such a reason.

### Consequences

- Good: each rule is small, tested on its own boundaries, and explained in the status change.
- Good: the lifecycle cannot reach an inconsistent status; every transition is an explicit, tested rule.
- Bad: flaky tests that fail rarely (for example 1 in 50 runs) are only caught by the same-commit strategy, which needs
  a re-run or Surefire reruns. Teams without either see them late.
- Bad: renaming a test starts a new history.
- Bad: manual decisions are stored in the database, not in the event stream; replaying into an empty database
  recomputes automatic statuses but loses quarantines.
