package dev.mc2p.common.activity;

import dev.mc2p.common.role.Role;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks recent MCP endpoint activity per authenticated client. Streamable HTTP is
 * stateless, so "connected" is approximated by clients active within a configurable
 * window. Entries are pruned lazily on read/write.
 */
public final class ClientActivityTracker {

    /** One tracked client's activity summary. */
    public record Entry(
            String name, Role role, String tokenId, String remoteIp, long lastSeenMillis, long requestCount) {}

    private final Duration window;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * @param window how long an entry stays "active" after its last request; a
     *     non-positive duration means entries never expire
     */
    public ClientActivityTracker(Duration window) {
        this.window = window;
    }

    /** Records one authenticated request from the given client. */
    public void record(String name, Role role, String tokenId, String remoteIp) {
        long now = System.currentTimeMillis();
        entries.compute(
                name,
                (k, prev) -> prev == null
                        ? new Entry(name, role, tokenId, remoteIp, now, 1)
                        : new Entry(
                                prev.name(),
                                prev.role(),
                                prev.tokenId(),
                                prev.remoteIp(),
                                now,
                                prev.requestCount() + 1));
    }

    /** Clients with requests within the activity window, ordered by last seen (most recent first). */
    public List<Entry> active() {
        long now = System.currentTimeMillis();
        long cutoff = window.toMillis() > 0 ? now - window.toMillis() : Long.MIN_VALUE;
        entries.entrySet().removeIf(e -> e.getValue().lastSeenMillis() < cutoff);
        List<Entry> result = new ArrayList<>(entries.values());
        result.sort((a, b) -> Long.compare(b.lastSeenMillis(), a.lastSeenMillis()));
        return result;
    }

    /** The configured activity window. */
    public Duration window() {
        return window;
    }
}
