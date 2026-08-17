package dev.mc2p.common.validate;

import java.util.List;
import java.util.Locale;

import dev.mc2p.common.role.Role;

/**
 * Server-side command policy: a per-role allowlist plus a deny list that applies even to
 * admin. Matching is exact on the first token, with a trailing {@code *} entry enabling
 * prefix matching. Deny takes precedence over allow.
 */
public final class CommandPolicy {

    private final List<String> opsAllowlist;
    private final List<String> adminAllowlist;
    private final List<String> deny;
    private final int maxCommandLength;

    public CommandPolicy(List<String> opsAllowlist, List<String> adminAllowlist, List<String> deny,
            int maxCommandLength) {
        this.opsAllowlist = normalize(opsAllowlist);
        this.adminAllowlist = normalize(adminAllowlist);
        this.deny = normalize(deny);
        this.maxCommandLength = Math.max(1, maxCommandLength);
    }

    private static List<String> normalize(List<String> list) {
        if (list == null) {
            return List.of();
        }
        return list.stream().filter(s -> s != null && !s.isBlank()).map(s -> s.trim().toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * Evaluates whether the given command may run for the role. Deny list is always
     * enforced, even for admin.
     *
     * @return a result reason: null if allowed, otherwise a short message
     */
    public String rejectionReason(String command, Role role) {
        if (command == null || command.isBlank()) {
            return "empty command";
        }
        if (command.length() > maxCommandLength) {
            return "command exceeds max length " + maxCommandLength;
        }
        String token = Validators.firstToken(command);
        if (matchesAny(token, deny)) {
            return "command is on the deny list";
        }
        List<String> allowlist = role == Role.ADMIN ? adminAllowlist : opsAllowlist;
        if (allowlist.isEmpty()) {
            return role == Role.ADMIN ? "admin allowlist is empty" : "no commands allowed for " + role;
        }
        if (matchesAny(token, allowlist)) {
            return null;
        }
        return "command is not allowed for " + role;
    }

    public boolean isAllowed(String command, Role role) {
        return rejectionReason(command, role) == null;
    }

    private static boolean matchesAny(String token, List<String> list) {
        for (String entry : list) {
            if (entry.equals("*")) {
                return true;
            }
            if (entry.endsWith("*") && token.startsWith(entry.substring(0, entry.length() - 1))) {
                return true;
            }
            if (entry.equals(token)) {
                return true;
            }
        }
        return false;
    }
}