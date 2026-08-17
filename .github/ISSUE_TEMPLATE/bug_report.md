---
name: Bug report
about: Report something that isn't working as expected
title: "[Bug] "
labels: bug
assignees: ""
---

## Description

A clear and concise description of what's broken.

## Topology

Which deployment shape are you running?

- [ ] Single server (Paper, standalone)
- [ ] Multi server (Velocity proxy + backends)

## Environment

- MC2P version: <!-- e.g. 0.1.0, commit hash -->
- Paper version:
- Velocity version (multi only):
- Java version:
- Client/agent used: <!-- e.g. Claude Desktop, npx MCP client -->

## Steps to reproduce

1. ...
2. ...
3. ...

## Expected behavior

What did you expect to happen?

## Actual behavior

What actually happened? Include error messages, stack traces, or audit-log lines.

## Configuration

Redact all secrets (tokens, passwords). Include the relevant `config.yml` sections only —
do not paste full files.

```yaml
# redacted config.yml snippet
```

## Security impact

Does this bug weaken any control point (TLS, auth, audit, validation)? If you believe so,
report it privately per the guidance in `docs/SECURITY.md` instead of this template.

- [ ] No security impact
- [ ] Possible security impact — I reported it through a private channel

## Additional context

Any other relevant information, logs, or screenshots.
