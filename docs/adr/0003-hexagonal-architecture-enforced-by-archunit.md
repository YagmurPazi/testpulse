# 0003. Hexagonal architecture enforced by ArchUnit

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

The core of TestPulse is domain logic: flakiness detection strategies and a test state machine with forbidden
transitions. This logic must be testable without Spring, a database or Kafka, and must not change when an
infrastructure detail changes (for example, a new report source or a new notification channel).

Architecture rules that live only in a diagram erode quickly: one convenient import is enough. How do we structure the
services, and how do we keep that structure intact?

## Considered options

Structure:

1. Classic layered architecture (controller → service → repository)
2. Hexagonal architecture (ports and adapters)

Enforcement of the hexagonal rules:

- A. Convention and code review only
- B. Separate Maven modules per layer (domain, application, adapters), so the compiler enforces the rules
- C. Packages per layer, rules enforced by ArchUnit tests

## Decision outcome

Chosen options: **2, hexagonal architecture**, enforced by **C, ArchUnit rules on packages.**

Each service uses the same layout: `domain`, `application` (`port/in`, `port/out`, `service`), `adapter/in`,
`adapter/out` and `config`. The rules live once, in the shared `architecture-rules` module, and every service applies
them with a single test:

- the domain depends only on itself and the JDK (no Spring, JPA, Kafka or event contracts),
- the application layer depends only on the domain and the JDK,
- dependencies point inwards: adapters and config may use the core, never the other way round,
- adapters do not depend on each other; they meet only through ports.

The rules are tested themselves: a compliant sample service must pass and each kind of violation must be rejected.

Why not option B: three Maven modules per service would mean at least nine modules for three services, with more
build configuration and slower builds, for a team of one. ArchUnit gives the same guarantee as an executable,
readable specification, and coverage and mutation testing can still be scoped to the domain packages.

### Consequences

- Good: domain logic is unit-tested with plain Java, which keeps tests fast and makes mutation testing practical.
- Good: infrastructure is replaceable behind ports; new channels or report sources are new adapters.
- Good: a violation fails the build with a precise message, instead of being caught (or missed) in review.
- Bad: violations are caught at test time, not compile time; an IDE will happily suggest the forbidden import.
- Bad: because the application layer is framework free, use cases cannot carry `@Transactional`. Transaction
  boundaries are set in configuration or adapters, which is more explicit but less familiar.
- Bad: mapping between domain objects and persistence or message models adds code.
- Note: the rules currently allow empty layers so they can be applied before the layers have classes. This should be
  tightened once each service has a domain, so a mistyped package name cannot silently disable a rule.
