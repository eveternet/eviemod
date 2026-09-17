package dev.eviemod.paintbrush;

import java.nio.file.Path;
import java.util.*;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NameDocumentTest {
    @TempDir Path directory;
    private List<Style> styles(NameDocument doc) {
        var list = new ArrayList<Style>();
        doc.styled().render(Style.EMPTY).visit((s, text) -> { text.codePoints().forEach(cp -> list.add(s)); return Optional.empty(); }, Style.EMPTY);
        return list;
    }
    @Test void togglesOnlyHighlightedCharactersInEitherDirection() {
        var doc = new NameDocument(StyledName.plain("Hello world"));
        doc.toggle(5, 0, 0);
        assertTrue(styles(doc).subList(0, 5).stream().allMatch(Style::isBold));
        assertTrue(styles(doc).subList(5, 11).stream().noneMatch(Style::isBold));
        doc.toggle(1, 4, 1);
        assertFalse(styles(doc).getFirst().isItalic()); assertTrue(styles(doc).get(2).isItalic());
        doc.toggle(0, 5, 0);
        assertTrue(styles(doc).stream().noneMatch(Style::isBold));
        doc.toggle(4, 4, 2);
        assertTrue(styles(doc).stream().noneMatch(Style::isObfuscated));
    }
    @Test void appliesGradientOnlyToSelectedUnicodeCodePoints() {
        var doc = new NameDocument(StyledName.plain("A😀BCZ"));
        doc.color(1, 5, 0xff0000, 0x0000ff);
        var s = styles(doc);
        assertNull(s.get(0).getColor()); assertNull(s.get(4).getColor());
        assertEquals(0xff0000, s.get(1).getColor().getValue());
        assertEquals(0x800080, s.get(2).getColor().getValue());
        assertEquals(0x0000ff, s.get(3).getColor().getValue());
        assertEquals("A😀BCZ", doc.styled().render(Style.EMPTY).getString());
    }
    @Test void editingPreservesFormattingOnUnchangedCharacters() {
        var doc = new NameDocument(StyledName.plain("red blue"));
        doc.toggle(4, 8, 0);
        doc.edit("red green blue");
        assertTrue(styles(doc).subList(10, 14).stream().allMatch(Style::isBold));
        assertTrue(styles(doc).subList(0, 10).stream().noneMatch(Style::isBold));
        doc.edit("blue");
        assertTrue(styles(doc).stream().allMatch(Style::isBold));
        doc.edit(""); assertNull(doc.styled());
        doc.edit("new"); assertTrue(styles(doc).stream().noneMatch(Style::isBold));
    }
    @Test void segmentedStylesAndSpacesSurviveSavingAndReload() throws Exception {
        var doc = new NameDocument(StyledName.plain("a b"));
        doc.toggle(1, 2, 2); doc.toggle(2, 3, 4);
        var store = new NameOverrides(directory.resolve("names.json")); UUID id = UUID.randomUUID();
        store.setStyle(id, doc.styled()); store.load();
        var loaded = new NameDocument(store.getStyle(id));
        assertEquals(styles(doc), styles(loaded));
        assertEquals("a b", loaded.text());
    }
    @Test void migratesWholeNameGradientIntoEditableCharacters() {
        var old = new StyledName("ABC", 0xff0000, 0x0000ff, true, true, false, false, false);
        var doc = new NameDocument(old); doc.toggle(1, 2, 0);
        assertTrue(styles(doc).get(0).isBold()); assertFalse(styles(doc).get(1).isBold());
        assertEquals(0x800080, styles(doc).get(1).getColor().getValue());
    }
}
