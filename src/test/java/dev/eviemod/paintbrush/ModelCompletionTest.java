package dev.eviemod.paintbrush;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelCompletionTest {
    @Test void completesNamespaceAndUnqualifiedPathPrefixes() {
        var models = List.of("minecraft:diamond_sword", "minecraft:stone", "mypack:diamond_wand");
        assertEquals(List.of("minecraft:diamond_sword", "mypack:diamond_wand"), ModelCompletion.matches(models, "diamond"));
        assertEquals(List.of("mypack:diamond_wand"), ModelCompletion.matches(models, "mypack:"));
        assertEquals(List.of("minecraft:stone"), ModelCompletion.matches(models, "MINECRAFT:ST"));
        assertTrue(ModelCompletion.matches(models, "unknown:").isEmpty());
        assertEquals(models, ModelCompletion.matches(models, ""));
    }
}
