package dev.mc2p.common.net;

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

    private Cidr(byte[] network, int prefix) {
        this.network = network;
        this.prefix = prefix;
    }

    public static Cidr parse(String spec) {
        String[] parts = spec.trim().split("/", 2);
        try {
            byte[] addr = InetAddress.getByName(parts[0]).getAddress();
            int bits = addr.length * 8;
            int prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : bits;
            if (prefix < 0 || prefix > bits) {
                throw new IllegalArgumentException("Invalid prefix length in CIDR: " + spec);
            }
            return new Cidr(addr, prefix);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid CIDR address: " + spec, e);
        }
    }

    public boolean contains(InetAddress address) {
        byte[] addr = address.getAddress();
        if (addr.length != network.length) {
            return false;
        }
        int fullBytes = prefix / 8;
        int remainingBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (addr[i] != network[i]) {
                return false;
            }
        }
        if (remainingBits > 0) {
            int mask = 0xFF << (8 - remainingBits);
            return (addr[fullBytes] & mask) == (network[fullBytes] & mask);
        }
        return true;
    }

    public static List<Cidr> parseAll(List<String> specs) {
        List<Cidr> result = new ArrayList<>();
        if (specs == null) {
            return result;
        }
        for (String spec : specs) {
            if (spec == null || spec.isBlank()) {
                continue;
            }
            result.add(parse(spec));
        }
        return result;
    }

    public static boolean anyMatch(List<Cidr> rules, InetAddress address) {
        for (Cidr rule : rules) {
            if (rule.contains(address)) {
                return true;
            }
        }
        return false;
    }
}
