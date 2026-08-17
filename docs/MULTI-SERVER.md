# Multi-server deployment (Velocity proxy)

The common architecture: a Velocity proxy in front of N backend Paper servers. The proxy
plugin hosts the **single public MCP endpoint** (one TLS port). Backends open zero HTTP
ports — they communicate with the proxy only over the `mc2p:rpc` plugin-messaging
channel.

```
Agent ──HTTPS(1 public port)──▶ Velocity / MC2P proxy plugin
                                    │ plugin messaging  mc2p:rpc  (internal network)
                                    ▼
                    Paper backend A  ·  Paper backend B  ·  …
```

## Setup

1. **Backends**: drop `mc2p-plugin.jar` into `plugins/` on each Paper server. Set the
   shared secret env var:

   ```sh
   export MC2P_PROXY_SECRET="<shared-secret>"
   ```

   With `mode: auto` (the default), the plugin resolves to **backend** mode when this
   env var is present. A backend opens no ports.

2. **Proxy**: drop `mc2p-proxy.jar` into `plugins/` on Velocity. Set:

   ```sh
   export MC2P_PROXY_SECRET="<shared-secret>"     # same value as backends
   export MC2P_TOKEN_READER="..."                  # agent tokens
   export MC2P_TOKEN_OPS="..."
   export MC2P_TOKEN_ADMIN="..."
   ```

   Open one port (default 8443) on the proxy box.

3. Generate everything reproducibly:

   ```sh
   ./gradlew deploy:run --args="gen-config --topology multi --host proxy.example.com --port 8443"
   ```

## Configuration

```yaml
serverId: proxy-01
mcp:
  bind: 0.0.0.0
  port: 8443
  endpoint: /mcp
  tls: { mode: selfsigned }
auth:
  tokens: { reader: env:MC2P_TOKEN_READER, ops: env:MC2P_TOKEN_OPS, admin: env:MC2P_TOKEN_ADMIN }
  ip-allowlist: []
  rate-limit: { tokens-per-second: 5, burst: 20 }
servers:            # optional Velocity server-name → serverId map
  lobby: main-01    # auto-discovered backends use the Velocity name as serverId
  survival: survival-02
rpc:
  secret-env: MC2P_PROXY_SECRET
  channel: mc2p:rpc
  timeout-ms: 5000
  max-chunks: 8
audit:
  file: logs/mcp-proxy-audit.log
  max-mb: 50
  max-files: 5
```

- The `servers` map is optional. Backends not listed use their Velocity server name as
  the `serverId`. Backends are registered on Velocity startup and on
  `ServerRegisteredEvent`, and removed on `ServerUnregisteredEvent`.

## Routing model

Every relayed tool gets a `server` parameter:

- **Player-targeted tools** (`player_message`, `player_kick`, `player_teleport`,
  `player_gamemode`, `player_effect`, `player_ban`, `player_unban`,
  `player_whitelist_add`, `player_whitelist_remove`, `player_info`, `player_stats`)
  auto-resolve the backend from the player's current server when `server` is omitted.
  Pass `server` explicitly to override.
- **Read tools** accept `server="*"` to broadcast to all connected backends. Results are
  aggregated as `{"servers": {serverId: result}, "errors": {serverId: error}}`.
- **Control tools** (`command_execute`, `block_set`, `server_restart`, `server_stop`)
  require an explicit `server` and are never broadcast.
- **Proxy-level tools**:
  - `fleet_status` — aggregates `server_status` across all backends.
  - `player_locate {uuid}` — reports the current server of an online player.

## SSE / resource notifications

Backends push player join/leave events over `mc2p:rpc` (`{"t":"event"}`). The proxy
forwards them to connected MCP clients as `resources/list-changed` notifications, so
agent resource listings stay fresh without polling.

## Auditing in the proxy topology

- The proxy audits every destructive relay (fail-closed) before forwarding.
- The backend audits the action again when it executes. Two audit entries: one at the
  proxy (`action=relay`), one at the backend (`action=execute`).

## Tuning per backend

Each backend keeps its own `config.yml`: `command_execute` allowlists, coordinate
limits, `features.blockEdit`, restart strategy, and rate limits. The proxy is a relay —
it does not widen backend policy. A proxy-authorized admin can only do what each backend
permits.