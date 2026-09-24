package dev.eviemod.paintbrush;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkyBlockUuidTest {
    @Test void acceptsOnlyTheOriginalFullUuidShape() {
        String full = "f1aa0d73-40e3-4950-ba29-e3b66745d2bd";
        assertEquals(UUID.fromString(full), SkyBlockUuid.parse(full));
        assertEquals(UUID.fromString(full), SkyBlockUuid.parse(full.toUpperCase(java.util.Locale.ROOT)));
        for (String malformed : new String[]{"", "1-1-1-1-1", "f1aa0d73-40e3-4950-ba29-e3b66745d2b",
            "f1aa0d73-40e3-4950-ba29-e3b66745d2bd ", "g1aa0d73-40e3-4950-ba29-e3b66745d2bd"})
            assertNull(SkyBlockUuid.parse(malformed), malformed);
        assertNull(SkyBlockUuid.parse(null));
    }
}
