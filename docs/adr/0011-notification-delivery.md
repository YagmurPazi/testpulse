# 0011. Notification delivery

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

notification-service tells the team when a test's status changes, by e-mail and in Slack. It consumes
`TestStatusChanged` events, which are delivered at least once ([ADR-0006](0006-transactional-outbox.md)), and it sends
messages through systems outside its database: an SMTP server and a Slack webhook.

Three questions: which changes deserve a message, how to avoid sending a message twice, and what to do when a
channel is down.

## Considered options

What to notify:

1. Every status change
2. Changes that need attention or record a decision, and only recent ones

Idempotency:

- A. None: a redelivered event sends its messages again
- B. One processed-event marker per event, as in analysis-service ([ADR-0008](0008-idempotent-consumer-and-error-handling.md))
- C. One delivery record per event **and channel**

Errors:

- X. Retry without limit, as in analysis-service
- Y. Retry a bounded number of times, then give up

## Decision outcome

Chosen options: **2**, **C** and **Y.**

### What is notified

| Change | Notified as | Why |
|---|---|---|
| any → Flaky | "Flaky test" | Its failures are probably not the change under test: the team should know before re-running blindly |
| Flaky → Quarantined | "Quarantined" | A team decision others should know about |
| Quarantined → Suspicious | "Released from quarantine" | Ditto |
| Flaky → Stable | "Recovered" | Good news closes the loop |
| Stable ↔ Suspicious | – | Suspicious means "watching"; most suspicious tests return to stable unnoticed |

Changes older than 24 hours are not notified. A new consumer group reads the topic from the beginning, so the first
start would otherwise send a burst of old news. A notification nobody needs trains people to ignore all of them,
the same way flaky tests train them to ignore red builds.

### Idempotency per channel

`delivered_notification(event_id, channel)` is written **after** a channel accepted the message, one row per
channel. A redelivered event finds the rows and sends nothing again. If Slack fails and e-mail succeeds, the retry
sends only to Slack.

Why not B: one marker for the whole event would either resend the e-mail when Slack fails, or lose the Slack message.
Exactly once is not possible: a crash between sending and recording sends that message again, because an e-mail
cannot be part of a database transaction. A rare duplicate message is acceptable; a lost one is worse.

### Bounded retries

A failed delivery is retried 6 times with exponential backoff (1 s doubling to 30 s, about a minute in total), then
given up and logged. Unreadable events are skipped at once.

Why not X, which analysis-service uses: there, skipping an event loses data and corrupts the statuses that follow.
Here, a Slack webhook that is down for hours would hold back every later notification of its partition, including
the e-mails. Losing one message is the smaller harm.

### Design

- `NotificationPolicy` decides, `NotificationFactory` words the message per kind (Factory), and every
  `NotificationChannel` receives every notification and formats it its own way (Observer). A new channel, such as
  Microsoft Teams, is one new class.
- A channel without configuration does not exist: Slack needs a webhook URL from the environment, e-mail can be
  switched off. The webhook URL is a secret and never logged.
- Mailpit is the SMTP server locally, so every e-mail can be read at http://localhost:8025.

### Consequences

- Good: no burst of old messages on first start, no duplicate messages on redelivery, and one broken channel does not
  silence the others.
- Bad: a notification can be lost when a channel is down for more than a minute. A dead letter topic, from which
  failed notifications can be replayed, is planned after the MVP.
- Bad: every notification goes to one list of recipients. Routing per repository or team needs a configuration
  model that the MVP does not have.
- Bad: the delivery table grows with every notification; rows older than the maximum age can be deleted.
