package dev.eviemod.features.skyblock;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HypixelAddressTest {
    @Test void acceptsCanonicalHostSubdomainsCaseRootDotAndPorts() {
        for (String address : new String[]{"hypixel.net", "mc.hypixel.net", "alpha.hypixel.net",
                "one.two.hypixel.net", "MC.HYPIXEL.NET", " hypixel.net ", "mc.hypixel.net:25565",
                "hypixel.net.", "mc.hypixel.net.:25565", "test-1.hypixel.net:65535"})
            assertTrue(SkyblockContext.isHypixelAddress(address), address);
    }

    @Test void rejectsUnrelatedDomainsAndMalformedAddresses() {
        assertFalse(SkyblockContext.isHypixelAddress(null));
        for (String address : new String[]{"", "localhost", "127.0.0.1", "[::1]:25565", "evilhypixel.net",
                "hypixel.net.evil.example", "mc.hypixel.net.evil.example", "not-hypixel.net",
                ".hypixel.net", "foo..hypixel.net", "-foo.hypixel.net", "foo-.hypixel.net",
                "foo_bar.hypixel.net", "user@mc.hypixel.net", "https://mc.hypixel.net",
                "mc.hypixel.net/path", "mc.hypixel.net#fragment", "mc.hypixel.net..",
                "mc.hypixel.net:", "mc.hypixel.net:0", "mc.hypixel.net:65536",
                "mc.hypixel.net:-1", "mc.hypixel.net:25565:123", "mc.hypixel.net:abc",
                "a".repeat(64) + ".hypixel.net", ("a".repeat(60) + ".").repeat(5) + "hypixel.net"})
            assertFalse(SkyblockContext.isHypixelAddress(address), address);
    }
}
