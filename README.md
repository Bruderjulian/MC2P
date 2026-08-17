# MC2P — Minecraft MCP Bridge

An MCP server that gives an AI agent control of a Paper Minecraft server, with security
as the primary design driver.

The MCP server lives **inside the plugin** (no separate process, no reverse proxy). It
embeds the official Java MCP SDK and serves MCP Streamable HTTP on a single TLS port.

Two deployment topologies from one codebase:

- **Single server** — the Paper plugin serves MCP itself on its own port (default 8443).
- **Multi server** — a Velocity proxy plugin hosts one public MCP port and routes tool
  calls to backend Paper servers over plugin messaging (`mc2p:rpc`). Backends open
  **zero** HTTP ports.

## Modules

| Module   | Description                                                                 |
|----------|-----------------------------------------------------------------------------|
| `common` | Shared: roles, tokens, CIDR, rate limiting, audit, RPC wire format, config. |
| `plugin` | Paper backend plugin (standalone MCP server or zero-port RPC backend).      |
| `proxy`  | Velocity proxy plugin (public MCP endpoint, RPC relay, fleet routing).      |
| `deploy` | CLI that generates tokens, secrets, configs, and agent `mcpServers` JSON.   |

## Requirements

- Paper 1.20.x / 1.21.x, Java 17+ (plugin targets Java 21 for current Paper).
- Velocity 3.3+ for the multi-server proxy.
- Gradle 9.x (Java 21 JDK) to build.

## Build

```sh
export JAVA_HOME="/path/to/jdk-21"
./gradlew build            # builds all modules incl. shaded jars
```

Artifacts:

- `plugin/build/libs/mc2p-plugin.jar` — drop into `plugins/` on Paper.
- `proxy/build/libs/mc2p-proxy.jar` — drop into `plugins/` on Velocity.

## Quick start — single server

1. Drop `mc2p-plugin.jar` into `plugins/`, start the server.
2. Set the token env vars (see `config.yml`): `MC2P_TOKEN_READER`, `MC2P_TOKEN_OPS`,
   `MC2P_TOKEN_ADMIN`.
3. Open one port (8443) on the Paper box.
4. Point an MCP agent at `https://<host>:8443/mcp` with a bearer token. With the default
   `tls.mode=selfsigned` export and trust the generated cert — never
   `insecureSkipVerify`.

## Quick start — multi server (Velocity proxy)

1. Drop `mc2p-plugin.jar` on each backend Paper server; drop `mc2p-proxy.jar` on Velocity.
2. Set `MC2P_PROXY_SECRET` (shared secret) on the proxy **and** every backend.
3. Set the proxy token env vars on the proxy: `MC2P_TOKEN_READER`, `MC2P_TOKEN_OPS`,
   `MC2P_TOKEN_ADMIN`.
4. Open one port (8443) on the proxy box. Backends open nothing.
5. Point an MCP agent at `https://proxy:8443/mcp`. Tool calls target backends via the
   `server` parameter; player tools auto-resolve from the player's current server.

See [docs/MULTI-SERVER.md](docs/MULTI-SERVER.md) for the full routing model.

## Configuration

- `plugin/src/main/resources/config.yml` — backend plugin config.
- `proxy/src/main/resources/config.yml` — proxy config.

Generate everything (tokens, secret, configs, agent snippet) with:

```sh
./gradlew deploy:run --args="gen-config --topology multi --host proxy.example.com --port 8443"
```

## Security

Security is the primary design driver. Read [docs/SECURITY.md](docs/SECURITY.md):
role tiers, defense in depth, fail-closed audit for destructive tools, token handling,
TLS modes, command allowlists, and the RPC handshake.

## Verification

```sh
./gradlew test
```

MCP conformance can be checked against a running endpoint with the SDK's own
`npx @modelcontextprotocol/conformance`.