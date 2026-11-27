# Architecture

TestPulse is described here with the [C4 model](https://c4model.com): the system in its environment, the containers it
consists of, and the components inside one of them. Decisions behind the structure are in the
[architecture decision records](adr/README.md).

## Level 1: System context

```mermaid
C4Context
    title System context of TestPulse

    Person(team, "Development team", "Writes the tests, reacts to flaky ones")
    System(testpulse, "TestPulse", "Detects flaky tests from CI test reports and tracks test health")
    System_Ext(github, "GitHub Actions", "Runs CI, keeps JUnit XML reports as artifacts for 90 days")
    System_Ext(slack, "Slack", "Team chat")
    System_Ext(mail, "Mail server", "SMTP; Mailpit locally")

    Rel(team, testpulse, "Looks up flaky tests, quarantines them", "Browser")
    Rel(github, testpulse, "Uploads reports", "HTTPS, optional CI step")
    Rel(testpulse, github, "Imports reports of completed runs", "REST API, every 5 min")
    Rel(testpulse, slack, "Posts status changes", "Incoming webhook")
    Rel(testpulse, mail, "Sends status changes", "SMTP")
    Rel(mail, team, "Delivers e-mails")
    Rel(slack, team, "Shows messages")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

## Level 2: Containers

```mermaid
C4Container
    title Containers of TestPulse

    Person(team, "Development team")
    System_Ext(github, "GitHub Actions")
    System_Ext(slack, "Slack")
    System_Ext(mail, "Mail server")

    System_Boundary(tp, "TestPulse") {
        Container(dashboard, "Dashboard", "React, nginx", "Repositories, trends, test history; proxies /api/ to analysis-service")
        Container(ingestion, "ingestion-service", "Java 21, Spring Boot", "Parses JUnit XML, imports GitHub artifacts, publishes TestRunIngested")
        Container(analysis, "analysis-service", "Java 21, Spring Boot", "Flakiness detection, test status lifecycle, query API")
        Container(notification, "notification-service", "Java 21, Spring Boot", "Decides what to notify, sends e-mail and Slack messages")
        ContainerQueue(kafka, "Kafka", "KRaft", "testpulse.test-runs, testpulse.test-status-changes")
        ContainerDb(dbi, "ingestion DB", "PostgreSQL", "Runs, results, outbox")
        ContainerDb(dba, "analysis DB", "PostgreSQL", "Tests, executions, status changes, outbox")
        ContainerDb(dbn, "notification DB", "PostgreSQL", "Deliveries per event and channel")
    }

    Rel(team, dashboard, "Uses", "HTTPS")
    Rel(dashboard, analysis, "Queries, quarantines", "JSON/HTTP")
    Rel(github, ingestion, "Uploads reports", "multipart/HTTP")
    Rel(ingestion, github, "Imports artifacts", "REST")
    Rel(ingestion, dbi, "Reads, writes", "JDBC")
    Rel(ingestion, kafka, "TestRunIngested", "outbox relay")
    Rel(kafka, analysis, "TestRunIngested")
    Rel(analysis, dba, "Reads, writes", "JDBC")
    Rel(analysis, kafka, "TestStatusChanged", "outbox relay")
    Rel(kafka, notification, "TestStatusChanged")
    Rel(notification, dbn, "Reads, writes", "JDBC")
    Rel(notification, mail, "E-mail", "SMTP")
    Rel(notification, slack, "Message", "Webhook")

    UpdateLayoutConfig($c4ShapeInRow="4", $c4BoundaryInRow="1")
```

- **Event-driven:** the services never call each other; they exchange events through Kafka
  ([ADR-0004](adr/0004-kafka-as-event-backbone.md)). A service that is down misses nothing: it reads on when it is back.
- **Database per service:** one PostgreSQL instance, three databases and three login roles. No service can read another
  service's data.
- **Transactional outbox:** a service stores its events with its data in one transaction and a relay publishes them
  ([ADR-0006](adr/0006-transactional-outbox.md)), so data and events never disagree.

## Level 3: Components of analysis-service

Every service has the same hexagonal structure ([ADR-0003](adr/0003-hexagonal-architecture-enforced-by-archunit.md));
analysis-service holds the most logic.

```mermaid
C4Component
    title Components of analysis-service

    ContainerQueue(kafka, "Kafka")
    ContainerDb(dba, "analysis DB", "PostgreSQL")
    Container(dashboard, "Dashboard")

    Container_Boundary(analysis, "analysis-service") {
        Component(listener, "TestRunIngestedListener", "adapter/in/messaging", "Parses and validates events; skips poison messages")
        Component(web, "Query and status controllers", "adapter/in/web", "REST API, RFC 9457 problem details")
        Component(process, "ProcessTestRunService", "application", "Idempotent run processing: record executions, evaluate tests")
        Component(status, "ChangeTestStatusService", "application", "Manual quarantine and release")
        Component(detector, "FlakinessDetector", "domain, Strategy", "Same-commit conflict and flip-rate strategies")
        Component(lifecycle, "MonitoredTest and TestState", "domain, State", "Stable, Suspicious, Flaky, Quarantined")
        Component(persistence, "JDBC repositories and queries", "adapter/out/persistence", "Row locks, LATERAL window query, read model")
        Component(outbox, "Outbox publisher", "adapter/out/outbox", "TestStatusChanged into the outbox")
    }

    Rel(kafka, listener, "TestRunIngested")
    Rel(listener, process, "ProcessTestRunUseCase")
    Rel(dashboard, web, "JSON/HTTP")
    Rel(web, status, "ChangeTestStatusUseCase")
    Rel(web, persistence, "TestQueries (read side)")
    Rel(process, detector, "Evaluates history")
    Rel(process, lifecycle, "Applies verdicts")
    Rel(status, lifecycle, "Quarantine, release")
    Rel(process, persistence, "Ports out")
    Rel(process, outbox, "StatusChangePublisher")
    Rel(persistence, dba, "JDBC")
    Rel(outbox, dba, "Same transaction")

    UpdateLayoutConfig($c4ShapeInRow="3", $c4BoundaryInRow="1")
```

ArchUnit checks the arrows that matter: the domain and application code depend on no framework, adapters do not depend
on each other, and nothing depends on the configuration.

## One run through the system

What happens between a CI run and an e-mail, with the guarantees at each step.

```mermaid
sequenceDiagram
    autonumber
    participant CI as GitHub Actions
    participant I as ingestion-service
    participant K as Kafka
    participant A as analysis-service
    participant N as notification-service
    participant M as Mail server

    CI->>I: JUnit XML (upload, or imported artifact)
    Note over I: parse, then one transaction:<br/>run + TestRunIngested in the outbox<br/>(same identity again: no-op)
    I-->>CI: 201 Created
    I->>K: relay publishes TestRunIngested (key: repository)
    K->>A: TestRunIngested
    Note over A: one transaction: processed-event marker,<br/>executions, evaluation, status changes,<br/>TestStatusChanged in the outbox
    A->>K: relay publishes TestStatusChanged
    K->>N: TestStatusChanged
    Note over N: policy: notable and recent?<br/>per channel: already delivered?
    N->>M: e-mail
    Note over N: record the delivery
```

| Step | Failure | What happens |
|---|---|---|
| 2 | the same run is uploaded twice | the identity exists, the stored run is returned, no event ([ADR-0005](adr/0005-event-contracts-and-wire-format.md)) |
| 4 | Kafka is down | events wait in the outbox and are published when it is back |
| 4 | Kafka can never accept an event (above its message limit) | marked rejected and logged; the events behind it continue. Large runs are split into parts so this does not happen in practice ([ADR-0013](adr/0013-large-runs-and-rejected-events.md)) |
| 5 | an event arrives twice | the processed-event marker makes the second one a no-op ([ADR-0008](adr/0008-idempotent-consumer-and-error-handling.md)) |
| 5 | an event can never be processed | skipped and logged, the partition continues |
| 5 | the analysis database is down | retried without limit: nothing is lost, the order is kept |
| 8 | the mail server is down | retried for about a minute, then given up ([ADR-0011](adr/0011-notification-delivery.md)) |

Events of one repository share a Kafka partition, so each repository's runs and status changes are processed in order.

## Topics

| Topic | Producer | Consumers | Key | Partitions | Retention |
|---|---|---|---|---|---|
| `testpulse.test-runs` | ingestion-service | analysis-service | repository | 3 | forever: replay rebuilds the analysis |
| `testpulse.test-status-changes` | analysis-service | notification-service | repository | 3 | 30 days |
