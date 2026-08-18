<div align="center">

# MC2P — Minecraft MCP Bridge

An MCP server that gives AI agents safe, audited control of Paper Minecraft servers.

[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21-007396.svg)](#requirements)
[![Paper](https://img.shields.io/badge/Paper-1.20.x%E2%80%931.21.x-e3f2fd.svg)](#requirements)
[![Velocity](https://img.shields.io/badge/Velocity-3.3%2B-9cf.svg)](#requirements)

</div>

MC2P embeds the official Java [MCP SDK](https://modelcontextprotocol.io/) **inside a
Minecraft plugin** — no separate process, no reverse proxy. Your server publishes a
standard Model Context Protocol (Streamable HTTP) endpoint that Claude, other MCP
clients, or any agent can call to inspect and operate the server in real time.

Security is the primary design driver. Every control point is layered: TLS with
certificate pinning, named bearer tokens (each key maps to a name + per-token
restrictions), IP allowlisting, rate limiting, fail-closed auditing, and a `confirm`
gate on every destructive action.

---

## Features

- **In-process MCP server** — the SDK runs inside the plugin and serves MCP Streamable
  HTTP on a single TLS port (`/mcp`).
- **Two topologies from one codebase** — single server, or a multi-server fleet behind a
  Velocity proxy with a single public port.
- **Restriction layers instead of roles** — per-token, global, and server restrictions
  on tools, commands, and worlds, merged most-restrictive-wins.
- **Live world awareness** — status, worlds, players, blocks, regions, entities, and
  per-player stats.
- **Safe mutation** — messaging, teleporting, effects, whitelisting, and console
  commands behind allow/deny lists.
- **Fail-closed audit log** — every destructive action is recorded *before* it executes;
  if the log can't be written, the action is refused.
- **`/mc2p` in-game admin console** — setup, status, reload, named token create/revoke/list.
- **One-command setup** — `/mc2p setup` generates missing API tokens (and the shared proxy
  secret) and prints the agent-ready `mcpServers.json` template.

## How it works

```
                ┌──────────────────────────────────────────────┐
                │              Paper server                     │
                │                                                │
  MCP client ──HTTPS (TLS /mcp)──▶  MC2P plugin                  │
   (Claude,       bearer token        ┌───────────────────┐     │
    any agent)        │               │  MCP SDK (in-proc)│     │
                       │               └───────────────────┘     │
                       │                     │ tools            │
                       │                     ▼                   │
                       │              Tool layer (restriction)     │
                       │                     │                   │
                       │                     ▼                   │
                       │              Audit (fail-closed)        │
                       └────────────▶  Paper API / console       │
                └──────────────────────────────────────────────┘
```

The MCP server lives entirely inside the plugin. One port, one process, no extra moving
parts — see [Security](#security) for why that design is important.

## Topologies

**Single server** — the Paper plugin serves MCP itself on its own port (default `8443`).

```
Agent ──HTTPS:8443──▶ Paper / MC2P plugin
```

**Multi server** — a Velocity proxy plugin hosts the one public MCP port and routes tool
calls to backend Paper servers over plugin messaging (`mc2p:rpc`). Backends open **zero**
HTTP ports.

```
Agent ──HTTPS:8443──▶ Velocity / MC2P proxy
                         │ plugin messaging  mc2p:rpc  (internal)
                         ▼
              Paper A   ·   Paper B   ·   …
```

Player-targeted tools auto-resolve the right backend from the player's current server;
read tools can broadcast across the fleet with `server="*"`. See
[docs/MULTI-SERVER.md](docs/MULTI-SERVER.md).

## Modules

| Module   | Description                                                                 |
|----------|-----------------------------------------------------------------------------|
| `common` | Shared core: restrictions, tokens, CIDR, rate limiting, audit, RPC wire format, config, setup. |
| `plugin` | Paper backend plugin — standalone MCP server or zero-port RPC backend.      |
| `proxy`  | Velocity proxy plugin — public MCP endpoint, RPC relay, fleet routing.      |

## Requirements

| Component   | Version                                  |
|-------------|------------------------------------------|
| Paper       | 1.20.x / 1.21.x (plugin bytecode: Java 21) |
| Velocity    | 3.3+ (multi-server proxy only)           |
| JDK         | 21 to build; all jars target Java 21    |
| Gradle      | 9.x                                      |

## Build

```sh
export JAVA_HOME="/path/to/jdk-21"
./gradlew build            # builds all modules incl. shaded jars
```

Artifacts:

- `plugin/build/libs/mc2p-plugin.jar` — drop into `plugins/` on Paper.
- `proxy/build/libs/mc2p-proxy.jar` — drop into `plugins/` on Velocity.

## Quick start — single server

1. Drop `mc2p-plugin.jar` into `plugins/` and start the server. With `mode: auto` and no
   proxy secret it runs **standalone** and, if no tokens exist yet, generates a `default`
   token on first start (printed to the console exactly once).
2. Run `/mc2p setup` — it prints any freshly generated tokens, writes
   `plugins/MC2P/mcpServers.json`, and shows the agent config to paste into your MCP
   client (fill in `<HOST>` and a token for the agent).
3. Open **one** port (8443) on the Paper box.
4. Point an MCP agent at `https://<host>:8443/mcp` with a bearer token. With the default
   `tls.mode: selfsigned`, export and trust the generated cert — never
   `insecureSkipVerify`.

## Quick start — multi server (Velocity proxy)

1. Drop `mc2p-plugin.jar` on each backend Paper server; drop `mc2p-proxy.jar` on Velocity.
2. Start the proxy and run `/mc2p setup` there. It generates the API tokens, prints the
   **shared proxy secret** once, activates all registered backends, and writes the agent
   `mcpServers.json` template.
3. Put that same shared secret on **every** backend — as `MC2P_PROXY_SECRET` env var or in
   `plugins/MC2P/proxy-secret` — then reload/restart them. With `mode: auto` a backend with
   the secret resolves to *backend* mode and opens no ports; a backend without it refuses
   to start.
4. Open **one** port (8443) on the proxy box. Backends open nothing.
5. Point an MCP agent at `https://proxy:8443/mcp`. Tool calls target backends via the
   `server` parameter; player tools auto-resolve from the player's current server.

### In-plugin setup (`/mc2p setup`)

`/mc2p setup` on either plugin does what the old `deploy` CLI did, from the console:

- **Standalone plugin** — generates a missing API token (shown once, persisted in
  `tokens.yml`), writes `plugins/MC2P/mcpServers.json`, and prints the client config with
  the real port.
- **Proxy** — generates missing API tokens, ensures the shared `MC2P_PROXY_SECRET`
  (generated + printed once if unset; persisted in `plugins/mc2p-proxy/proxy-secret`),
  re-activates all backends, and prints the client config.
- **Backend plugin** — no tokens of its own; `/mc2p setup` reports the active serverId and
  channel, or tells you the proxy secret is missing (the plugin disables itself in that
  case).

Secrets are always shown exactly once per generation; configs reference them by env var or
the 0600 secret file.

## MCP client configuration

```json
{
  "mcpServers": {
    "mc2p": {
      "type": "streamable-http",
      "url": "https://<host>:8443/mcp",
      "headers": { "Authorization": "Bearer <TOKEN>" }
    }
  }
}
```

Use a token you minted for the agent (`/mc2p token create <name>`). `/mc2p setup` prints
the template with your port already filled in.

## Tools

Tool availability is decided by the caller's merged restrictions (per-token × global ×
server), not a role. The lists below are the full catalog; an agent only sees the tools
its restrictions allow. Destructive tools are audited fail-closed and require
`confirm: true`.

### Read tools

| Tool              | Description                                                     |
|-------------------|-----------------------------------------------------------------|
| `server_status`   | TPS, tick, uptime, players, worlds, plugins, heap, restart strategy. |
| `world_list`      | Worlds with dimension, spawn, loaded-chunk counts.              |
| `plugin_list`     | Loaded plugins with version and enabled state.                  |
| `player_list`     | Online players with uuid, name, ping, gamemode, health, location. |
| `player_info`     | One player by UUID: effects, dimension, operator status.        |
| `player_stats`    | Bukkit statistics snapshot by UUID.                             |
| `block_get`       | Single block: material, block data, biome, light, chunk loaded. |
| `region_get`      | Bounded block dump (region size capped by config).              |
| `entity_list`     | Entities in a world, type-filtered and paginated.               |
| `entity_info`     | One entity by UUID: type, position, health, passengers.         |

### Mutation tools

| Tool                 | Description                                              |
|----------------------|----------------------------------------------------------|
| `player_message`     | Send chat as console; formatting optional.               |
| `player_kick` *      | Kick a player with optional reason.                      |
| `player_teleport`    | Teleport to coordinates or another player.               |
| `player_gamemode`    | Set survival/creative/adventure/spectator.               |
| `player_effect`      | Apply a potion effect with duration and amplifier.       |
| `player_ban` *       | Ban a player by UUID.                                    |
| `player_unban` *     | Unban a player by UUID.                                  |
| `player_whitelist_add`| Add a player to the whitelist.                          |
| `player_whitelist_remove` * | Remove a player from the whitelist.             |
| `command_execute` *  | Run console commands gated by the command restriction.   |
| `block_set` *        | Set one block from a curated material allowlist.         |
| `server_restart` *   | Restart via the configured strategy.                     |
| `server_stop` *      | Graceful stop.                                           |

\* Destructive tools are **audited fail-closed** and require `confirm: true` in the
arguments.

### Proxy-level (multi server)

| Tool             | Description                                                    |
|------------------|----------------------------------------------------------------|
| `fleet_status`   | Aggregate `server_status` across all connected backends.       |
| `player_locate`  | Report the current backend server of an online player.         |

The proxy re-exposes every backend tool with an extra `server` parameter. Read tools
accept `server="*"` to broadcast; control tools require an explicit `server`.

## In-game admin console

On the Paper server, with `mc2p.admin`:

```
/mc2p setup                                generate missing tokens + print agent config
/mc2p status                               view mode, endpoint, tokens, audit log
/mc2p reload                               reload config.yml
/mc2p activity                             show clients active in the last N minutes
/mc2p token create <name>           mint a named token (shown once)
/mc2p token revoke <name>           permanently remove a runtime token
/mc2p token disable <name>          suspend a token without removing it
/mc2p token enable <name>           re-activate a disabled token
/mc2p token revoke <name>           revoke a runtime token by name
/mc2p token list                    list tokens by name, restrictions and token id
```

## Configuration

- `plugin/src/main/resources/config.yml` — backend plugin config (mode, TLS, restrictions,
  rate limits, command allow/deny, audit); `backend.yml` is the backend-mode variant.
- `proxy/src/main/resources/config.yml` — proxy config (server map, RPC, restrictions, audit).

`config.yml` is regenerated on first run if missing, and the file your server actually
uses is the one next to the jar — copy your changes there after editing the template.
Key sections:

| Section          | What it controls                                                        |
|------------------|------------------------------------------------------------------------|
| `mcp`            | Bind address, port, endpoint path, body limit, TLS mode.                |
| `auth`           | IP allowlist, rate limit, activity window. Tokens live in `tokens.yml`. |
| `global-restrictions` / `server-restrictions` | Per-layer tool/command/world allow & deny lists. |
| `limits`         | Coordinate clamp, max region blocks, entity limit, command length.     |
| `audit`          | Audit log path, rotation size and count.                               |
| `restart`        | Restart strategy (`auto` / `spigot-restart` / `host-restart` / `disabled`). |

TLS modes: `selfsigned` (default, generates a PKCS12 keystore on first run),
`keystore` (bring your own; set `MC2P_KEYSTORE_PW`), `none-behind-proxy` (host panel
terminates TLS), `none` (plaintext — loud warning, local testing only).

## Security

Security is the primary design driver. Read [docs/SECURITY.md](docs/SECURITY.md) for the
full threat model and every control point. Highlights:

- **Defense in depth** — no single layer is trusted on its own.
- **TLS + cert pinning** — never `insecureSkipVerify`.
- **Tokens** — stored as SHA-256 hashes, compared in constant time; rotation and
  revocation persist across restarts.
- **Fail-closed audit** — destructive actions are logged before they run; audit failure
  means refusal.
- **`confirm` gate** — every destructive tool additionally requires `confirm: true`.
- **Command policy** — `command_execute` is gated by the merged `commands` restriction
  (allow/deny lists) that wins over everything.
- **Proxy isolation** — the proxy never widens backend policy; a proxy-authorized token
  can only do what each backend permits.

## Verification

```sh
./gradlew test
```

MCP conformance can be checked against a running endpoint with the SDK's own
`npx @modelcontextprotocol/conformance`. A health check is exposed at
`GET /healthz` on the MCP port.

## Documentation

- [docs/SECURITY.md](docs/SECURITY.md) — threat model, restrictions, defense in depth, TLS modes, secrets.
- [docs/MULTI-SERVER.md](docs/MULTI-SERVER.md) — multi-server topology, routing model, SSE notifications.

## Contributing

Contributions are welcome. This project has a strong stance on safety — when in doubt,
read the security docs first.

Please read **[CONTRIBUTING.md](CONTRIBUTING.md)** for the full setup, guidelines, and
pull request workflow, and **[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)** for our community
standards.

```sh
git clone https://github.com/Bruderjulian/MC2P.git
cd MC2P
export JAVA_HOME="/path/to/jdk-21"
./gradlew build
./gradlew test
```

Found a security issue? **Do not open a public issue.** Report it privately via the
[Security Advisory](https://github.com/Bruderjulian/MC2P/security/advisories) feature —
see [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE) © 2026 BruderJulian
