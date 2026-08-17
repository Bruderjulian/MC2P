---
name: Other
about: Any change that isn't a bug fix, feature, or documentation
title: "[Other] "
labels: ""
assignees: ""
---

## Summary

<!-- A concise description of the change. -->

## Reason

<!-- Why is this change needed? -->

## Changes

<!-- Bullet points describing what changed. -->

## Security impact

<!-- If this touches any control point (TLS, auth, audit, validation), describe the
impact explicitly. -->

- [ ] No security impact
- [ ] Affects authorization / role checks
- [ ] Affects audit / fail-closed behavior
- [ ] Affects TLS / transport security
- [ ] Affects input validation or limits

## Verification

- [ ] `./gradlew build` passes
- [ ] `./gradlew test` passes

## Documentation

- [ ] README updated if user-facing behavior changed
- [ ] `docs/SECURITY.md` updated if a control point changed
- [ ] `docs/MULTI-SERVER.md` updated if the proxy relay changed

## Checklist

- [ ] Commits are focused with descriptive messages
- [ ] No secrets or credentials introduced
- [ ] Code follows existing module boundaries (`common` / `plugin` / `proxy`)