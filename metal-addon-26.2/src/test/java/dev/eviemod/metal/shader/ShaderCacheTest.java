package dev.eviemod.metal.shader;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ShaderCacheTest {
    @TempDir Path dir;

    @Test void corruptCacheFallsBackToTranslation() throws Exception {
        ShaderCache.enable(dir);
        try {
            for (int size : new int[]{-1, Integer.MAX_VALUE, 20}) {
                Files.write(dir.resolve("bad"), ByteBuffer.allocate(4).putInt(size).array());
                assertNull(ShaderCache.load("bad"));
            }
            var result = new ShaderTranslator.Result("msl", "main", Map.of("Projection", 0), Map.of(),
                    Map.of(), Map.of("Position", 0), Map.of(), Map.of(), 0);
            ShaderCache.store("valid", result);
            assertEquals(result, ShaderCache.load("valid"));
        } finally { ShaderCache.enable(null); }
    }
}
