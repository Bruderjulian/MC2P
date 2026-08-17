# MC2P security model

Security is the primary design driver. This document describes the threat model and
every control point. The guiding principle is **defense in depth**: no single layer is
trusted on its own, and destructive actions **fail closed**.

## Threat model

Assumed attacker:

- Has access to an MCP client that is authenticated as a low-privilege role (or can
  present arbitrary requests to the endpoint).
- Can send arbitrary HTTP requests to the public MCP port.
- Can reach the internal network between the proxy and backends **if** they compromise
  another service on that network.
- Does **not** have the owner's secrets (tokens, keystore password, proxy secret).

The owner is assumed to have limited hosting access: they can open ports in a panel but
may not have root, systemd, console, or file access. Every control point has a
hosting-friendly fallback (see the spec, section 2.3).

## Role tiers

| Role   | Reach                                                        |
|--------|--------------------------------------------------------------|
| reader | Read-only: status, worlds, players, blocks, entities.        |
| ops    | Player-facing actions + gated `command_execute` allowlist.   |
| admin  | Everything, including bans, whitelist, `block_set`, restarts.|

Roles are cumulative (`admin.can(ops)` is true). Every tool declares a minimum role; the
transport layer authenticates the caller, and the tool layer **re-checks** the role.

## Defense in depth

1. **TLS + cert pinning** — the MCP endpoint is served over TLS. Default `selfsigned`
   mode generates a keystore; clients must pin the exported cert (never
   `insecureSkipVerify`). Modes: `selfsigned`, `keystore`, `none-behind-proxy`,
   `none` (loud warning, plaintext).
2. **Bearer tokens → roles** — tokens are stored as SHA-256 hashes only (never the
   plaintext), compared in constant time. Rotation/revocation persists across restarts.
3. **IP allowlist (optional)** — CIDR blocks that may reach the MCP endpoint.
4. **Rate limiting** — per-client token-bucket on the HTTP filter.
5. **Tool-level authorization** — enforced at the tool layer regardless of transport
   (HTTP bearer or RPC envelope), so a bug in one layer cannot bypass another.
6. **Audit, fail-closed** — every destructive action is appended to a JSON-lines audit
   log *before* it executes. If the entry cannot be written, the action is **refused**.
   Tokens are never logged; only a derived token id.
7. **Confirm flag** — destructive tools additionally require `confirm: true` in the
   arguments.
8. **RPC handshake** — backends authenticate the proxy with the shared `proxySecret`
   over plugin messaging; the proxy re-sends `hello` before every request so the
   handshake is enforced on each call. Requests from unauthenticated senders are
   dropped. The trust window is 5 minutes.
9. **Command policy** — `command_execute` is gated by per-role allowlists plus a global
   deny list. Deny always wins, even for admin. Prefix matches via `prefix*`.
10. **Input validation** — world keys, coordinates (clamped), entity types, materials
    (curated allowlist), message/reason lengths, pagination, and command length are all
    validated server-side.

## TLS modes

| Mode              | Meaning                                                                        |
|-------------------|--------------------------------------------------------------------------------|
| `selfsigned`      | Default. Generates a PKCS12 keystore on first run. Clients must pin the cert.  |
| `keystore`        | Bring your own keystore; set the password env var (`MC2P_KEYSTORE_PW`).        |
| `none-behind-proxy`| The host panel terminates TLS in front of this port.                          |
| `none`            | Plaintext. Loud startup warning; only for local testing.                       |

## Secrets

- Tokens and the proxy secret are resolved from `env:VAR` or `file:path` sources, or
  (warned against) plaintext in `config.yml`.
- Never log a token or secret. The audit log stores the first 4 bytes of the SHA-256
  hash as a token id only.
- `/mc2p setup` generates strong random (256-bit) tokens and the shared proxy secret;
  configs reference them by env var or a 0600 secret file, so the plaintext appears only
  in the one-time setup output.

## Destructive tool list

These tools are audited (fail-closed) and require `confirm: true`: `player_kick`,
`player_ban`, `player_unban`, `player_whitelist_remove`, `block_set`,
`command_execute`, `server_restart`, `server_stop`. The proxy audits destructive
relays again before forwarding them to a backend.

## `command_execute` policy

- `ops-allowlist`: commands OPS may run (defaults: `gamemode`, `tp`, `teleport`,
  `weather`, `time`, `effect`, `clear`).
- `admin-allowlist`: defaults to `*` (everything) — the deny list still applies.
- `deny`: always denied regardless of allowlist or role (defaults: `stop`, `restart`,
  `save-off`, `save-all`, `kick-all`, `op`).

`command_execute` rejects commands containing shell metacharacters and strips the args
from the audit detail (only the command name is recorded).

## Proxy (multi-server)

- The proxy re-checks role and re-audits destructive relays before forwarding.
- `*` broadcasts are only allowed on read tools.
- The proxy forwards backend RPC pushes (player join/leave) to connected MCP clients as
  `resources/list-changed` SSE notifications.