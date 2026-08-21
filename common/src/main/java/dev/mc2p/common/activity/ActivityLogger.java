package dev.mc2p.common.activity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import dev.mc2p.common.exceptions.AuditWriteException;

/**
 * Append-only JSON-lines audit log with size-based rotation. Destructive
 * actions
 * <em>fail closed</em>: if the entry cannot be written, an
 * {@link AuditWriteException} is
 * thrown and the caller must abort the action. Tokens are never logged; only
 * the derived
 * token id.
 */
public final class ActivityLogger {

    private final Path file;
    private final long maxBytes;
    private final int maxFiles;
    private final Object lock = new Object();
    private long approximateSize;

    public ActivityLogger(final Path file, final int maxMb, final int maxFiles) {
        this.file = file;
        this.maxBytes = maxMb * 1024L * 1024L;
        this.maxFiles = Math.max(1, maxFiles);
        try {
            if (Files.isRegularFile(file)) {
                this.approximateSize = Files.size(file);
            }
            final Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (final Exception e) {
            throw new AuditWriteException("Cannot initialize audit log at " + file, e);
        }
    }

    /**
     * Appends an audit entry.
     *
     * @throws AuditWriteException if the entry cannot be written (fail-closed)
     */
    public void log(final String clientName, final String tokenId, final String serverId, final String tool,
            final String action) {
        log(clientName, tokenId, serverId, tool, action, "");
    }

    public void log(final String clientName, final String tokenId, final String serverId, final String tool,
            final String action, final String detail) {
        final StringBuilder sb = new StringBuilder(256);
        sb.append('{')
                .append("\"ts\":")
                .append(Instant.now().toString())
                .append(",\"serverId\":")
                .append(serverId)
                .append(",\"tool\":")
                .append(tool)
                .append(",\"action\":")
                .append(action)
                .append(",\"client\":")
                .append(clientName == null ? "" : clientName)
                .append(",\"tokenId\":")
                .append(tokenId == null ? "" : tokenId)
                .append(",\"detail\":")
                .append(detail == null || detail.isEmpty() ? "{}" : detail)
                .append("}\n");
        writeLine(sb.toString());
    }

    private void writeLine(final String line) {
        synchronized (lock) {
            try {
                final Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                        file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                approximateSize += line.getBytes(StandardCharsets.UTF_8).length;
                if (approximateSize > maxBytes) {
                    rotate();
                }
            } catch (final Exception e) {
                throw new AuditWriteException("Failed to write audit entry to " + file, e);
            }
        }
    }

    private void rotate() {
        try {
            for (int i = maxFiles - 1; i >= 1; i--) {
                final Path from = Path.of(file + "." + i);
                final Path to = Path.of(file + "." + (i + 1));
                if (Files.exists(from)) {
                    Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            // rotate() is only reached right after a successful write, so the log file
            // exists.
            Files.move(file, Path.of(file + ".1"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            approximateSize = 0;
        } catch (final Exception e) {
            throw new AuditWriteException("Failed to rotate audit log " + file, e);
        }
    }

    public static String detail(final CharSequence json) {
        return json == null ? "" : json.toString();
    }

    public static String noDetail() {
        return "";
    }
}
