# Security Policy

Security is the primary design driver of MC2P. The full threat model, role tiers, and
every control point are documented in [docs/SECURITY.md](docs/SECURITY.md).

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Instead, report vulnerabilities privately using GitHub's Security Advisory feature:

- Use the **Report a vulnerability** button on the
  [Security tab](https://github.com/Bruderjulian/MC2P/security/advisories), or
- Email the maintainers directly with the details.

Include, where possible:

- A description of the vulnerability and the impact.
- Which control point it weakens (see `docs/SECURITY.md`): TLS, bearer tokens, role
  checks, audit/fail-closed behavior, IP allowlist, rate limiting, command policy, or
  input validation.
- Reproduction steps or a proof of concept.
- The topology and versions affected (`standalone` / `multi`, plugin/proxy version,
  Paper/Velocity versions).

You can expect an acknowledgement within a few days, and a fix as soon as one is
available. Please allow time for a fix before any public disclosure.

## Scope

In scope:

- The MC2P plugins (`plugin`, `proxy`) and the `common` core.
- The HTTP/MCP transport layer and RPC relay.

Out of scope:

- The Minecraft server itself, Velocity, or Paper — report those to their respective
  projects.
- Misconfiguration by operators (for example, `tls.mode: none` or plaintext tokens in
  `config.yml`), which are documented as unsafe.

## Supported versions

| Version | Supported          |
|---------|--------------------|
| latest  | Security fixes     |
| older   | Best-effort, on request |