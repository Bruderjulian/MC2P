package dev.mc2p.common.role;

/**
 * Client role tiers, ordered from least to most privileged.
 */
public enum Role {

    READER,
    OPS,
    ADMIN;

    /**
     * True if this role satisfies the given required role.
     */
    public boolean can(Role required) {
        return required == null || ordinal() >= required.ordinal();
    }

    public static Role fromString(String value) {
        if (value == null) {
            return null;
        }
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}