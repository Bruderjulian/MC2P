---
name: Feature
about: A new feature or enhancement for MC2P
title: "[Feature] "
labels: enhancement
assignees: ""
---

## Summary

<!-- A concise description of the feature. Link to the issue it resolves (e.g. "Closes #12"). -->

## Motivation

<!-- What problem does this solve? Who benefits and why? -->

## Proposed behavior

<!-- How does it work? Consider both topologies (standalone plugin and Velocity proxy
relay) if applicable. -->

## Security impact

<!-- MC2P is security-first. New functionality must stay within the existing role tiers,
audit/fail-closed behavior, and validation rules. Explain how. -->

- [ ] No security impact
- [ ] Introduces a new tool or changes a role requirement
- [ ] Affects audit / fail-closed behavior
- [ ] Affects TLS / transport security
- [ ] Affects input validation or limits

## Verification

<!-- How did you verify the feature? Tests must be included. -->

- [ ] `./gradlew build` passes
- [ ] `./gradlew test` passes
- [ ] New tests cover the behavior and the failure cases (denied role, invalid input, audit refusal)
- [ ] Behavior validated on a live server (describe topology)

## Documentation

- [ ] README updated for the new behavior (tool tables, config, quick start)
- [ ] `docs/SECURITY.md` updated if a control point or tool changed
- [ ] `docs/MULTI-SERVER.md` updated if the proxy relay changed

## Checklist

- [ ] Defaults are conservative (feature flags off unless opted in, allowlists tight)
- [ ] Commits are focused with descriptive messages
- [ ] No secrets or credentials introduced
- [ ] Code follows existing module boundaries (`common` / `plugin` / `proxy`)