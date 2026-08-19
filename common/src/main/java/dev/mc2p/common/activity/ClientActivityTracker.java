package dev.mc2p.common.activity;

import java.time.Duration;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Tracks recent MCP endpoint activity per authenticated client. Streamable HTTP
 * is
 * stateless, so "connected" is approximated by clients active within a
 * configurable
 * window. Entries are pruned lazily on read/write.
 */
public final class ClientActivityTracker {

    /** One tracked client's activity summary. */
    public record Entry(String tokenId, String name, RemoteIpList remoteIp, long lastSeenMillis, long requestCount) {
    }

    private long window;
    private final boolean isExpiring;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    /**
     * @param window how long an entry stays "active" after its last request; a
     *               non-positive duration means entries never expire
     */
    public ClientActivityTracker(final Duration window) {
        if (!window.isPositive()) {
            isExpiring = false;
            return;
        }
        isExpiring = true;
        try {
            this.window = window.abs().toMillis();
        } catch (Exception e) {
            this.window = Duration.ofDays(7).toMillis();
            throw new IllegalArgumentException("Invalid window duration: " + window, e);
        }
    }

    /** Records one authenticated request from the given client. */
    
    public void record(final String tokenId, final String name, final String remoteIp) {
        final long now = System.currentTimeMillis();
        entries.compute(
                tokenId,
                (k, prev) -> {
                    if (prev == null) {
                        return new Entry(tokenId, name, new RemoteIpList(remoteIp), now, 1);
                    } else {
                        prev.remoteIp().addIfAbsent(remoteIp);
                        return new Entry(prev.tokenId(), prev.name(), prev.remoteIp(), now, prev.requestCount() + 1);
                    }
                });
    }

    /**
     * Clients with requests within the activity window, ordered by last seen (most
     * recent first).
     */
    public List<Entry> active() {
        if (isExpiring) {
            final long cutoff = System.currentTimeMillis() - window;
            entries.entrySet().removeIf(e -> e.getValue().lastSeenMillis() < cutoff);
        }
        final List<Entry> result = new ArrayList<>(entries.values());
        result.sort((a, b) -> Long.compare(b.lastSeenMillis(), a.lastSeenMillis()));
        return result;
    }

    /** The configured activity window. */
    public long window() {
        return window;
    }

    public static class RemoteIpList {
        private transient String[] elementData;
        private int size;
        private static final int SOFT_MAX_LENGTH = Integer.MAX_VALUE - 8;
        private transient int modCount = 0;

        public RemoteIpList(final String str) {
            elementData = new String[] { str };
            size = 1;
        }

        public void addIfAbsent(String str) {
            final int expectedModCount = modCount;
            for (int i = 0; modCount == expectedModCount && i < size; i++) {
                if (str.equals(elementData[i])) {
                    return;
                }
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            modCount++;
            if (size == elementData.length) {
                elementData = grow(elementData.length);
            }
            elementData[size++] = str;
            return;
        }

        public int size() {
            return size;
        }

        public boolean isEmpty() {
            return size == 0;
        }

        private String[] grow(final int oldLength) {
            int prefLength = oldLength + Math.max(1, oldLength >> 1); // might overflow
            String[] copy = new String[prefLength <= SOFT_MAX_LENGTH ? prefLength : oldLength + 1];
            // for (int i = size; i-- != 0; copy[i] = elementData[i]) {}
            System.arraycopy(elementData, 0, copy, 0, oldLength);
            return copy;
        }

        public void forEach(Consumer<String> action) {
            final int expectedModCount = modCount;
            for (int i = 0; modCount == expectedModCount && i < size; i++) {
                action.accept(elementData[i]);
            }
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
        }

        public RemoteIpListIterator iterator() {
            return new RemoteIpListIterator(this);
        }

        public static class RemoteIpListIterator implements Iterator<String> {
            private int next;
            private final String[] array;
            private final int size;

            RemoteIpListIterator(final RemoteIpList list) {
                this.array = list.elementData;
                this.size = list.size;
                this.next = 0;
            }

            @Override
            public boolean hasNext() {
                return this.next < size;
            }

            @Override
            public String next() {
                if (!this.hasNext()) {
                    throw new NoSuchElementException();
                } else {
                    return array[this.next++];
                }
            }

            public int skip(int n) {
                if (n < 0) {
                    throw new IllegalArgumentException("Argument must be nonnegative: " + n);
                } else {
                    n = Math.min(n, size - this.next);
                    this.next += n;
                    return n;
                }
            }

            @Override
            public void forEachRemaining(Consumer<? super String> action) {
                while (this.next < size) {
                    action.accept(array[this.next++]);
                }
            }
        }
    }
}
