# 0002. Monorepo with Maven multi-module

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

TestPulse consists of three Java services, shared event contracts, shared architecture rules and a frontend.
How should the code be split into repositories and build units?

Constraints: one developer, limited time, and readers who should understand the whole system from a single place
and start it with one command.

## Considered options

1. One repository per service (polyrepo)
2. One repository, each service an independent Maven build
3. One repository, one Maven reactor with a parent POM (multi-module)

## Decision outcome

Chosen option: **3, a monorepo with a Maven multi-module build.**

- The parent POM manages all dependency and plugin versions in one place, inheriting from `spring-boot-starter-parent`.
- Modules: `event-contracts`, `architecture-rules`, and one module per service under `services/`.
- `./mvnw verify` at the root builds and checks everything; CI runs the same command.
- The dashboard lives in the same repository but has its own npm build.

### Consequences

- Good: a change to an event contract and all its consumers is one atomic commit and one pull request.
- Good: quality tooling (Checkstyle, SpotBugs, JaCoCo, PIT) is configured once and applied everywhere.
- Good: a reviewer finds the entire system, its documentation and its CI in one place.
- Bad: services are not released independently; a failing module blocks the build for all of them.
  Teams that deploy services on separate schedules usually prefer separate repositories.
- Bad: shared modules make coupling easy. Each shared module must stay small and dependency free; `event-contracts`
  in particular trades runtime independence for compile-time type safety (to be detailed in a later ADR).
