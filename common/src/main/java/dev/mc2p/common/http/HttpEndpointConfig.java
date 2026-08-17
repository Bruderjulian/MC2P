package dev.mc2p.common.http;

/**
 * The transport-shape part of the MCP HTTP endpoint, shared by the standalone backend
 * plugin and the proxy plugin so the (duplicated per spec) Jetty hosting layer stays
 * identical between modules.
 */
public record HttpEndpointConfig(
        String bind,
        int port,
        String endpoint,
        int bodyLimitBytes,
        String tlsMode,
        String keystore,
        String passwordEnv) {
}