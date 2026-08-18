# Contributing to MC2P

First off: thanks for taking the time to contribute.

MC2P is an MCP server that controls Minecraft servers, so **security is the primary
design driver**. Anything you contribute should respect the threat model in
[docs/SECURITY.md](docs/SECURITY.md) — read it before making changes that touch
authorization, auditing, TLS, or input validation.

By participating in this project you agree to follow our
[Code of Conduct](CODE_OF_CONDUCT.md).

## Table of contents

- [Code of Conduct](#code-of-conduct)
- [Ways to contribute](#ways-to-contribute)
- [Development setup](#development-setup)
- [Project structure](#project-structure)
- [Guidelines](#guidelines)
- [Pull request workflow](#pull-request-workflow)
- [Documentation](#documentation)
- [Reporting bugs](#reporting-bugs)
- [Reporting security issues](#reporting-security-issues)

## Code of Conduct

This project and everyone participating in it is governed by the
[Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this
code. Please report unacceptable behavior to the maintainers (see the Code of Conduct for
contact details).

## Ways to contribute

- **Report bugs** — open an issue using the bug report template.
- **Propose features** — open an issue using the feature request template.
- **Ask questions** — open an issue using the question template.
- **Fix bugs and add features** — follow the workflow below.
- **Improve docs** — fixes and clarifications to the README and `docs/` are always welcome.

## Development setup

Requires a JDK 21 and Gradle 9.x (the wrapper pins the version, so plain
`./gradlew` works).

```sh
git clone https://github.com/Bruderjulian/MC2P.git
cd MC2P
export JAVA_HOME="/path/to/jdk-21"
./gradlew build
```

## Project structure

| Module   | Description                                                                 |
|----------|-----------------------------------------------------------------------------|
| `common` | Shared core: roles, tokens, CIDR, rate limiting, audit, RPC wire format, config, setup. |
| `plugin` | Paper backend plugin — standalone MCP server or zero-port RPC backend.      |
| `proxy`  | Velocity proxy plugin — public MCP endpoint, RPC relay, fleet routing.      |

Keep changes inside the right module. New shared logic belongs in `common`, new tools in
`plugin` (`ReadTools` / `WriteTools`), proxy relays in `proxy` (`RelayTools`).

## Guidelines

- **Security first.** Any change that weakens a control point (TLS, auth, audit,
  validation) must be justified in the PR. Weaknesses are not merged without strong
  reasoning.
- **Stay fail-closed.** Destructive tools must keep their `confirm` gate and their
  fail-closed audit entry — in both the standalone plugin and the proxy relay.
- **Match the conventions.** Java 17 bytecode (21 for the plugin), UTF-8 sources, and
  existing module boundaries.
- **Don't introduce secrets.** Never commit tokens, passwords, or keystores. Use the
  `env:VAR` / `file:path` pattern from the existing configs.
- **Behave like an admin would expect.** Defaults must be conservative: feature flags
  off unless the operator opts in, allowlists tight, limits sane.
- **Update the docs.** If user-facing behavior changes, update the README and, where
  relevant, `docs/SECURITY.md` or `docs/MULTI-SERVER.md`.

## Pull request workflow

1. Fork the repo and create a branch:
   `git checkout -b feat/your-change`.
2. Make your change. Keep commits focused with descriptive messages.
3. Run the build locally:

   ```sh
   ./gradlew build
   ```

4. Push your branch and open a pull request using the
   [PR template](.github/PULL_REQUEST_TEMPLATE.md). Fill in the security-impact section —
   it is not optional.
5. A maintainer will review. Address review feedback with additional commits; keep the
   discussion on the PR.

## Documentation

- The user-facing docs live in the README and `docs/`.
- `docs/SECURITY.md` documents the threat model and every control point — update it when
  you change one.
- `docs/MULTI-SERVER.md` documents the proxy topology and routing model — update it when
  the relay changes.

## Reporting bugs

Open an issue using the [bug report template](.github/ISSUE_TEMPLATE/bug_report.md).
Include your topology (`standalone` / `multi`), versions, reproduction steps, and a
**redacted** config snippet. Never paste tokens or passwords.

## Reporting security issues

If you find a vulnerability, **do not open a public issue**. GitHub private vulnerability
reporting is preferred:

- Use the "Report a vulnerability" button on the
  [security tab](https://github.com/Bruderjulian/MC2P/security/advisories) of the
  repository, or
- Email the maintainers privately with a detailed description, reproduction steps, and
  impact assessment.

Describe the impact against the control points in `docs/SECURITY.md`. Please allow time
for a fix before any public disclosure.

## Attribution

This contributing guide is adapted from best practices for security-conscious
open-source projects.