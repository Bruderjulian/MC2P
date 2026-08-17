---
name: Fix
about: A bug fix for MC2P
title: "[Fix] "
labels: bug
assignees: ""
---

## Summary

<!-- A concise description of the bug and the fix. Link to the issue it resolves (e.g. "Fixes #12"). -->

## Root cause

<!-- What caused the bug? -->

## Changes

<!-- Bullet points describing the implementation. -->

## Security impact

<!-- MC2P is security-first. If the fix touches TLS, auth, audit, or validation, explain
explicitly how. Fail-closed behavior must never be weakened. -->

- [ ] No security impact
- [ ] Affects authorization / role checks
- [ ] Affects audit / fail-closed behavior
- [ ] Affects TLS / transport security
- [ ] Affects input validation or limits

## Verification

<!-- How did you verify the fix? Regression tests are required for bug fixes. -->

- [ ] `./gradlew build` passes
- [ ] `./gradlew test` passes
- [ ] Regression test added for the fixed behavior
- [ ] Reproduced the bug before, confirmed fixed after (describe topology)

## Documentation

- [ ] README updated if user-facing behavior changed
- [ ] `docs/SECURITY.md` updated if a control point changed
- [ ] `docs/MULTI-SERVER.md` updated if the proxy relay changed

## Checklist

- [ ] Commits are focused with descriptive messages
- [ ] No secrets or credentials introduced
- [ ] Code follows existing module boundaries (`common` / `plugin` / `proxy` / `deploy`)