package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.activity.ClientActivityTracker;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientActivityTrackerTest {

    @Test
    void recordsAndListsActiveClients() {
        ClientActivityTracker tracker = new ClientActivityTracker(Duration.ofMinutes(5));
        tracker.record("alice", "tid-1", "10.0.0.1");
        tracker.record("alice", "tid-1", "10.0.0.1");
        tracker.record("bob", "tid-2", "10.0.0.2");

        List<ClientActivityTracker.Entry> active = tracker.active();
        assertEquals(2, active.size());
        ClientActivityTracker.Entry alice = active.stream()
                .filter(e -> e.name().equals("alice"))
                .findFirst()
                .orElseThrow();
        assertEquals(2, alice.requestCount());
        assertEquals("10.0.0.1", alice.remoteIp());
        assertEquals("tid-1", alice.tokenId());
    }

    @Test
    void expiredEntriesArePruned() throws InterruptedException {
        ClientActivityTracker tracker = new ClientActivityTracker(Duration.ofMillis(50));
        tracker.record("alice", "tid-1", "10.0.0.1");
        Thread.sleep(120);
        assertTrue(tracker.active().isEmpty());
        assertTrue(tracker.window().toMillis() > 0);
    }

    @Test
    void nonPositiveWindowNeverExpires() throws InterruptedException {
        ClientActivityTracker tracker = new ClientActivityTracker(Duration.ofMillis(0));
        tracker.record("alice", "tid-1", "10.0.0.1");
        Thread.sleep(100);
        assertEquals(1, tracker.active().size());
    }

    @Test
    void activeOrderedMostRecentFirst() {
        ClientActivityTracker tracker = new ClientActivityTracker(Duration.ofMinutes(5));
        tracker.record("alice", "tid-1", "10.0.0.1");
        tracker.record("bob", "tid-2", "10.0.0.2");

        List<ClientActivityTracker.Entry> active = tracker.active();
        assertEquals("bob", active.get(0).name());
        assertEquals("alice", active.get(1).name());
    }
}
