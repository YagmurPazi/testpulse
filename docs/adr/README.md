# Architecture Decision Records

Each significant architecture decision is recorded here, with its context, the options considered and the
consequences we accept. Format: [MADR](https://adr.github.io/madr/), see [ADR-0001](0001-record-architecture-decisions.md).

| ADR | Title | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-monorepo-with-maven-multi-module.md) | Monorepo with Maven multi-module | Accepted |
| [0003](0003-hexagonal-architecture-enforced-by-archunit.md) | Hexagonal architecture enforced by ArchUnit | Accepted |
| [0004](0004-kafka-as-event-backbone.md) | Kafka as the event backbone | Accepted |
| [0005](0005-event-contracts-and-wire-format.md) | Event contracts and wire format | Accepted |
| [0006](0006-transactional-outbox.md) | Transactional outbox for publishing events | Accepted |
| [0007](0007-flakiness-detection-and-test-status.md) | Flakiness detection and the test status lifecycle | Accepted |
| [0008](0008-idempotent-consumer-and-error-handling.md) | Idempotent event consumption and error handling | Accepted |
| [0009](0009-dashboard-architecture.md) | Dashboard architecture | Accepted |
| [0010](0010-frontend-testing-strategy.md) | Frontend testing strategy | Accepted |
| [0011](0011-notification-delivery.md) | Notification delivery | Accepted |
| [0012](0012-github-actions-importer.md) | Importing CI runs from GitHub Actions | Accepted |
| [0013](0013-large-runs-and-rejected-events.md) | Large runs and events Kafka cannot accept | Accepted |

A new ADR gets the next number and starts as `Proposed`. Accepted ADRs are not rewritten; a changed decision gets a
new ADR that supersedes the old one.
