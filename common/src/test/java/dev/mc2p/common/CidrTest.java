package dev.mc2p.common;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.mc2p.common.net.Cidr;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class CidrTest {

    @Test
    void ipv4Matches() throws Exception {
        Cidr rule = Cidr.parse("10.0.0.0/8");
        assertTrue(rule.contains(InetAddress.getByName("10.1.2.3")));
        assertTrue(rule.contains(InetAddress.getByName("10.255.255.255")));
        assertFalse(rule.contains(InetAddress.getByName("11.0.0.1")));
    }

    @Test
    void ipv4HostOnly() throws Exception {
        Cidr rule = Cidr.parse("192.168.1.5");
        assertTrue(rule.contains(InetAddress.getByName("192.168.1.5")));
        assertFalse(rule.contains(InetAddress.getByName("192.168.1.6")));
    }

    @Test
    void ipv6Matches() throws Exception {
        Cidr rule = Cidr.parse("::1/128");
        assertTrue(rule.contains(InetAddress.getByName("::1")));
        assertFalse(rule.contains(InetAddress.getByName("::2")));
    }

    @Test
    void anyMatchAcrossRules() throws Exception {
        List<Cidr> rules = Cidr.parseAll(List.of("10.0.0.0/8", "172.16.0.0/12"));
        assertTrue(Cidr.anyMatch(rules, InetAddress.getByName("10.5.5.5")));
        assertTrue(Cidr.anyMatch(rules, InetAddress.getByName("172.16.9.9")));
        assertFalse(Cidr.anyMatch(rules, InetAddress.getByName("192.168.0.1")));
    }

    @Test
    void invalidInputRejected() {
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("not-an-address"));
        assertThrows(IllegalArgumentException.class, () -> Cidr.parse("10.0.0.0/33"));
        assertTrue(Cidr.parseAll(Arrays.asList("", "  ", null)).isEmpty());
    }
}
