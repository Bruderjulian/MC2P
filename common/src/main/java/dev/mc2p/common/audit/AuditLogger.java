package dev.mc2p.common.audit;

import dev.mc2p.common.role.Role;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * Append-only JSON-lines audit log with size-based rotation. Destructive actions
 * <em>fail closed</em>: if the entry cannot be written, an {@link AuditWriteException} is
 * thrown and the caller must abort the action. Tokens are never logged; only the derived
 * token id.
 */
public final class AuditLogger {

    /** Thrown when an audit entry cannot be persisted. */
    public static final class AuditWriteException extends RuntimeException {
        public AuditWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final Path file;
    private final long maxBytes;
    private final int maxFiles;
    private final Object lock = new Object();
    private long approximateSize;

    public AuditLogger(Path file, int maxMb, int maxFiles) {
        this.file = file;
        this.maxBytes = maxMb * 1024L * 1024L;
        this.maxFiles = Math.max(1, maxFiles);
        try {
            if (Files.isRegularFile(file)) {
                this.approximateSize = Files.size(file);
            }
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (Exception e) {
            throw new AuditWriteException("Cannot initialize audit log at " + file, e);
        }
    }

    /**
     * Appends an audit entry.
     *
     * @throws AuditWriteException if the entry cannot be written (fail-closed)
     */
    public void log(Role role, String clientName, String tokenId, String serverId, String tool, String action) {
        log(role, clientName, tokenId, serverId, tool, action, "");
    }

    public void log(
            Role role, String clientName, String tokenId, String serverId, String tool, String action, String detail) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{')
                .append("\"ts\":")
                .append(quote(Instant.now().toString()))
                .append(",\"serverId\":")
                .append(quote(serverId))
                .append(",\"tool\":")
                .append(quote(tool))
                .append(",\"action\":")
                .append(quote(action))
                .append(",\"client\":")
                .append(quote(clientName == null ? "" : clientName))
                .append(",\"role\":")
                .append(quote(role == null ? "none" : role.name().toLowerCase()))
                .append(",\"tokenId\":")
                .append(quote(tokenId == null ? "" : tokenId))
                .append(",\"detail\":")
                .append(detail == null || detail.isEmpty() ? "{}" : detail)
                .append("}\n");
        writeLine(sb.toString());
    }

    private void writeLine(String line) {
        synchronized (lock) {
            try {
                Path parent = file.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(
                        file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                approximateSize += line.getBytes(StandardCharsets.UTF_8).length;
                if (approximateSize > maxBytes) {
                    rotate();
                }
            } catch (Exception e) {
                throw new AuditWriteException("Failed to write audit entry to " + file, e);
            }
        }
    }

    private void rotate() {
        try {
            for (int i = maxFiles - 1; i >= 1; i--) {
                Path from = Path.of(file + "." + i);
                Path to = Path.of(file + "." + (i + 1));
                if (Files.exists(from)) {
                    Files.move(from, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            // rotate() is only reached right after a successful write, so the log file exists.
            Files.move(file, Path.of(file + ".1"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            approximateSize = 0;
        } catch (Exception e) {
            throw new AuditWriteException("Failed to rotate audit log " + file, e);
        }
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String detail(CharSequence json) {
        return json == null ? "" : json.toString();
    }

    public static String noDetail() {
        return "";
    }
}
