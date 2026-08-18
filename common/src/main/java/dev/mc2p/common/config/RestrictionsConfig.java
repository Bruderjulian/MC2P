package dev.mc2p.common.config;

import dev.mc2p.common.validate.Validators;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Restriction sets for the three controllable categories: {@code tools},
 * {@code commands}
 * and {@code worlds}. Each section has an enable switch (default <em>off</em>),
 * an
 * allowlist and a denylist.
 *
 * <p>
 * Sections are combined across layers (global, per-backend, per-token) with
 * most-restrictive-wins semantics: a deny in any <em>enabled</em> layer blocks;
 * an item
 * must satisfy every non-empty allowlist of the enabled layers; a disabled
 * section (or a
 * whole layer with {@code enabled: false}) contributes nothing. An empty
 * allowlist means
 * "everything except the deny list".
 */
public record RestrictionsConfig(boolean enabled, Section tools, Section commands, Section worlds) {

    /** A fully disabled restriction set: nothing is restricted. */
    public static final RestrictionsConfig DISABLED = new RestrictionsConfig(false, Section.DISABLED, Section.DISABLED,
            Section.DISABLED);

    public static final String KEY_TOOLS = "tools";
    public static final String KEY_COMMANDS = "commands";
    public static final String KEY_WORLDS = "worlds";

    /**
     * One restricted category. Matching is exact with {@code *} wildcard and
     * {@code prefix*} prefix support (commands are matched on their first token).
     */
    public record Section(boolean enabled, List<String> allowlist, List<String> denylist) {

        public static final Section DISABLED = new Section(false, List.of(), List.of());

        public Section {
            allowlist = normalize(allowlist);
            denylist = normalize(denylist);
        }

        public boolean allows(final String value) {
            if (!enabled || value == null) {
                return true;
            }
            if (matchesAny(value, denylist)) {
                return false;
            }
            return allowlist.isEmpty() || matchesAny(value, allowlist);
        }

        /** The effective deny list of this section (empty when disabled). */
        public List<String> activeDenylist() {
            return enabled ? denylist : List.of();
        }

        /** The effective allowlist of this section (empty when disabled). */
        public List<String> activeAllowlist() {
            return enabled ? allowlist : List.of();
        }

        public Map<String, Object> toMap() {
            final Map<String, Object> m = new LinkedHashMap<>();
            m.put("enabled", enabled);
            m.put("allowlist", allowlist);
            m.put("denylist", denylist);
            return m;
        }

        public static Section load(final Map<String, Object> yaml) {
            if (yaml == null || yaml.isEmpty()) {
                return DISABLED;
            }
            return new Section(
                    ConfigSupport.bool(yaml, "enabled", false),
                    ConfigSupport.strings(yaml, "allowlist"),
                    ConfigSupport.strings(yaml, "denylist"));
        }

        private static boolean matchesAny(final String value, final List<String> entries) {
            for (final String entry : entries) {
                if (entry.equals("*")) {
                    return true;
                }
                if (entry.endsWith("*") && value.startsWith(entry.substring(0, entry.length() - 1))) {
                    return true;
                }
                if (entry.equals(value)) {
                    return true;
                }
            }
            return false;
        }

        private static List<String> normalize(final List<String> list) {
            if (list == null) {
                return List.of();
            }
            final List<String> result = new ArrayList<>(list.size());
            for (final String s : list) {
                if (s != null && !s.isBlank()) {
                    result.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
            return List.copyOf(result);
        }
    }

    public RestrictionsConfig {
        tools = tools == null ? Section.DISABLED : tools;
        commands = commands == null ? Section.DISABLED : commands;
        worlds = worlds == null ? Section.DISABLED : worlds;
    }

    /** True if the given tool may be invoked under this restriction set. */
    public boolean isToolAllowed(final String toolName) {
        return !enabled || tools.allows(normalizeValue(toolName));
    }

    /** True if the given world key may be targeted under this restriction set. */
    public boolean isWorldAllowed(final String worldKey) {
        return !enabled || worlds.allows(normalizeValue(worldKey));
    }

    /**
     * True if the given console command may run under this restriction set (matched
     * on its first token).
     */
    public boolean isCommandAllowed(final String command) {
        if (!enabled || command == null || command.isBlank()) {
            return true;
        }
        return commands.allows(Validators.firstToken(command));
    }

    /**
     * Combines this restriction set with another, most-restrictive-wins. A layer
     * whose
     * top-level {@code enabled} flag is off contributes nothing.
     */
    public RestrictionsConfig merge(final RestrictionsConfig other) {
        if (other == null) {
            return this;
        }
        final boolean mergedEnabled = enabled || other.enabled;
        if (!mergedEnabled) {
            return DISABLED;
        }
        return new RestrictionsConfig(
                true,
                mergeSection(enabled ? tools : Section.DISABLED, other.enabled ? other.tools : Section.DISABLED),
                mergeSection(enabled ? commands : Section.DISABLED, other.enabled ? other.commands : Section.DISABLED),
                mergeSection(enabled ? worlds : Section.DISABLED, other.enabled ? other.worlds : Section.DISABLED));
    }

    public Map<String, Object> toMap() {
        final Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put(KEY_TOOLS, tools.toMap());
        m.put(KEY_COMMANDS, commands.toMap());
        m.put(KEY_WORLDS, worlds.toMap());
        return m;
    }

    /**
     * Parses a restrictions block; absent or empty maps yield {@link #DISABLED}.
     */
    public static RestrictionsConfig load(final Map<String, Object> yaml) {
        if (yaml == null || yaml.isEmpty()) {
            return DISABLED;
        }
        return new RestrictionsConfig(
                ConfigSupport.bool(yaml, "enabled", false),
                Section.load(ConfigSupport.map(yaml.get(KEY_TOOLS))),
                Section.load(ConfigSupport.map(yaml.get(KEY_COMMANDS))),
                Section.load(ConfigSupport.map(yaml.get(KEY_WORLDS))));
    }

    private static Section mergeSection(final Section a, final Section b) {
        final boolean enabled = a.enabled() || b.enabled();
        if (!enabled) {
            return Section.DISABLED;
        }
        final List<String> deny = union(a.activeDenylist(), b.activeDenylist());
        final List<String> allow = intersect(a.activeAllowlist(), b.activeAllowlist());
        return new Section(true, allow, deny);
    }

    private static List<String> union(final List<String> a, final List<String> b) {
        final List<String> result = new ArrayList<>(a);
        for (final String s : b) {
            if (!result.contains(s)) {
                result.add(s);
            }
        }
        return List.copyOf(result);
    }

    /**
     * Intersection of the two allowlists; empty lists contribute nothing (allow
     * all).
     */
    private static List<String> intersect(final List<String> a, final List<String> b) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        final List<String> result = new ArrayList<>();
        for (final String s : a) {
            if (b.contains(s)) {
                result.add(s);
            }
        }
        return List.copyOf(result);
    }

    private static String normalizeValue(final String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
