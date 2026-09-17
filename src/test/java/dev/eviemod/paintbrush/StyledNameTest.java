package dev.eviemod.paintbrush;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StyledNameTest {
    @TempDir Path directory;
    @Test void gradientPreservesUnicodeAndAllStyles() {
        var name = new StyledName("A✨😀Z", 0xff0000, 0x0000ff, true, true, true, true, true);
        var rendered = name.render(Style.EMPTY);
        assertEquals("A✨😀Z", rendered.getString());
        assertEquals(4, rendered.getSiblings().size());
        assertEquals(0xff0000, rendered.getSiblings().getFirst().getStyle().getColor().getValue());
        assertEquals(0x0000ff, rendered.getSiblings().getLast().getStyle().getColor().getValue());
        assertEquals(0xaa0055, rendered.getSiblings().get(1).getStyle().getColor().getValue());
        for (var part : rendered.getSiblings()) {
            assertTrue(part.getStyle().isBold()); assertTrue(part.getStyle().isItalic());
            assertTrue(part.getStyle().isObfuscated()); assertTrue(part.getStyle().isUnderlined());
            assertTrue(part.getStyle().isStrikethrough());
        }
        var single = new StyledName("X", 0, 0xffffff, false, false, false, false, false).render(Style.EMPTY);
        assertEquals(0, single.getSiblings().getFirst().getStyle().getColor().getValue());
    }
    @Test void roundTripAndLegacyMigration() throws Exception {
        UUID id = UUID.randomUUID(); Path file = directory.resolve("names.json");
        Files.writeString(file, "{\"" + id + "\":\"Legacy name\"}");
        var store = new NameOverrides(file); store.load();
        assertEquals("Legacy name", store.get(id));
        var name = new StyledName("Rainbow", 0xff88cc, 0x55ffff, true, false, true, false, true);
        store.setStyle(id, name);
        var loaded = new NameOverrides(file); loaded.load();
        assertEquals(name, loaded.getStyle(id));
        assertEquals(name, StyledName.fromJson(name.toJson()));
    }
    @Test void rejectsInvalidGradientAndSchemaWithoutReplacingStoredName() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new StyledName("X", null, 1, false, false, false, false, false));
        assertThrows(IllegalArgumentException.class, () -> new StyledName("X", -1, null, false, false, false, false, false));
        var json = StyledName.plain("X").toJson(); json.addProperty("bold", "yes");
        assertThrows(IllegalArgumentException.class, () -> StyledName.fromJson(json));
        UUID id = UUID.randomUUID(); Path file = directory.resolve("names.json");
        var store = new NameOverrides(file); store.set(id, "Keep me");
        Files.writeString(file, "{\"" + id + "\":" + json + "}");
        assertThrows(java.io.IOException.class, store::load);
        assertEquals("Keep me", store.get(id));
    }
}
