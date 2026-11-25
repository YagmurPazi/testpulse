# 0001. Record architecture decisions

- Status: Accepted
- Date: 2026-11-25

## Context and problem statement

TestPulse makes several decisions that are hard to reverse: how services communicate, how code is structured, which
infrastructure is used. Code shows *what* was decided, but not *why*, which alternatives were rejected, or what the
decision costs. Without that, a later change cannot tell whether a constraint is still relevant.

## Considered options

1. No written record; rely on commit messages and code comments
2. A single design document that is updated over time
3. Architecture Decision Records: one short, immutable file per decision

## Decision outcome

Chosen option: **3, Architecture Decision Records** in the [MADR](https://adr.github.io/madr/) format, stored in
`docs/adr/` next to the code.

- Each ADR covers one decision: context, considered options, outcome, consequences.
- ADRs are numbered and never rewritten once accepted. A changed decision is a new ADR marked as superseding the old one,
  so the history of reasoning is preserved.
- An ADR is written when a decision affects the structure of the system, is costly to reverse, or has a real
  alternative that someone could reasonably prefer.

### Consequences

- Good: the reasoning is reviewable in pull requests together with the code that implements it.
- Good: trade-offs are stated explicitly, including the costs we accept.
- Bad: writing an ADR takes time; small, easily reversible choices are deliberately left out.
