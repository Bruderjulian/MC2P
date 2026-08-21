package dev.mc2p.common.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal IPv4/IPv6 CIDR matcher for the optional IP allowlist.
 */
public final class Cidr {

    private final byte[] network;
    private final int prefix;

    private Cidr(final byte[] network, final int prefix) {
        this.network = network;
        this.prefix = prefix;
    }

    public boolean contains(final InetAddress address) {
        final byte[] addr = address.getAddress();
        if (addr.length != network.length) {
            return false;
        }
        final int fullBytes = prefix / 8;
        final int remainingBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (addr[i] != network[i]) {
                return false;
            }
        }
        if (remainingBits > 0) {
            final int mask = 0xFF << (8 - remainingBits);
            return (addr[fullBytes] & mask) == (network[fullBytes] & mask);
        }
        return true;
    }

    public static Cidr parse(final String spec) {
        final String[] parts = spec.trim().split("/", 2);
        try {
            final byte[] addr = InetAddress.getByName(parts[0]).getAddress();
            final int bits = addr.length * 8;
            final int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : bits;
            if (prefix < 0 || prefix > bits) {
                throw new IllegalArgumentException("Invalid prefix length in CIDR: " + spec);
            }
            return new Cidr(addr, prefix);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Invalid CIDR address: " + spec, e);
        }
    }

    public static List<Cidr> parseAll(final List<String> specs) {
        final List<Cidr> result = new ArrayList<>();
        if (specs == null) {
            return result;
        }
        for (final String spec : specs) {
            if (spec == null || spec.isBlank()) {
                continue;
            }
            result.add(parse(spec));
        }
        return result;
    }

    public static boolean anyMatch(final List<Cidr> rules, final InetAddress address) {
        for (final Cidr rule : rules) {
            if (rule.contains(address)) {
                return true;
            }
        }
        return false;
    }
}
