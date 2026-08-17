package dev.mc2p.proxy.rpc;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;

/**
 * Velocity listener for backend→proxy {@code mc2p:rpc} messages. The source of a
 * plugin-message event from a backend is the {@link RegisteredServer}; we consume our
 * channel and hand the payload to {@link BackendClient} for correlation/assembly.
 */
public final class RpcListener {

    private final BackendClient client;
    private final ChannelIdentifier channel;

    public RpcListener(BackendClient client, ChannelIdentifier channel) {
        this.client = client;
        this.channel = channel;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(channel)) {
            return;
        }
        if (!(event.getSource() instanceof RegisteredServer server)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        client.handleServerMessage(server, event.getData());
    }
}
