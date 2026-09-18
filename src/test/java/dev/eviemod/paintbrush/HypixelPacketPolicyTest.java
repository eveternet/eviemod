package dev.eviemod.paintbrush;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HypixelPacketPolicyTest {
    @Test void reloadOnlySelectedPacksStillUsingTheOldResources() {
        Object oldPack = new Object(), reloadedPack = new Object();
        assertTrue(TexturePackBypasser.needsReload(true, oldPack, oldPack));
        assertTrue(TexturePackBypasser.needsReload(true, null, oldPack));
        assertTrue(TexturePackBypasser.needsReload(true, null, null));
        assertFalse(TexturePackBypasser.needsReload(false, oldPack, oldPack));
        assertFalse(TexturePackBypasser.needsReload(true, reloadedPack, oldPack));
        assertFalse(TexturePackBypasser.needsReload(true, reloadedPack, null));
    }

    @Test void onlyTheVerifiedPackOnHypixelIsIntercepted() {
        var pack = new HypixelPackStore.Pack(84, "a".repeat(40), "https://resourcepacks.hypixel.net/SkyBlock/deployment/84.zip", "deployment");
        assertTrue(TexturePackBypasser.matches("mc.hypixel.net:25565", pack.url(), pack.hash(), pack));
        assertTrue(TexturePackBypasser.matches("HYPIXEL.NET", pack.url(), pack.hash().toUpperCase(), pack));
        for (String host : new String[]{"hypixel.net.evil.example", "fakehypixel.net", "localhost", "127.0.0.1", ""})
            assertFalse(TexturePackBypasser.matches(host, pack.url(), pack.hash(), pack));
        assertFalse(TexturePackBypasser.matches(null, pack.url(), pack.hash(), pack));
        assertFalse(TexturePackBypasser.matches("hypixel.net", pack.url(), "b".repeat(40), pack));
        assertFalse(TexturePackBypasser.matches("hypixel.net", pack.url() + "?other", pack.hash(), pack));
        assertFalse(TexturePackBypasser.matches("hypixel.net", pack.url(), pack.hash(), null));
    }
}
