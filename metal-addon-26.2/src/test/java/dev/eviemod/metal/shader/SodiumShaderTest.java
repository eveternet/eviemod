// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.shader;

import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.mtl.Mtl;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the actual pinned Sodium shader assets, including compact integers and R32Sint times. */
class SodiumShaderTest {
    @Test void compilesOpaqueAndCutoutTerrainInterfaces() throws Exception {
        boolean mac = System.getProperty("os.name").startsWith("Mac");
        if (mac) NativeLoader.load();
        for (boolean cutout : new boolean[]{false, true}) {
            String defines = "#define USE_VERTEX_COMPRESSION\n#define USE_FOG\n" + (cutout ? "#define USE_FRAGMENT_DISCARD\n" : "");
            var vs = ShaderTranslator.translate("sodium-terrain.vsh", source("blocks/block_layer_opaque.vsh", defines),
                    ShaderTranslator.Stage.VERTEX, Map.of("a_Position", 0, "a_Color", 1, "a_TexCoord", 2, "a_LightAndData", 3));
            var fs = ShaderTranslator.translate("sodium-terrain.fsh", source("blocks/block_layer_opaque.fsh", defines),
                    ShaderTranslator.Stage.FRAGMENT, vs.outputs());
            assertTrue(vs.buffers().containsKey("u_Globals"));
            assertTrue(vs.textures().containsKey("u_SectionTimeInfo"));
            assertTrue(vs.textures().containsKey("u_LightTex"));
            assertTrue(fs.textures().containsKey("u_BlockTex"));
            assertTrue(vs.defaults().keySet().containsAll(java.util.Set.of("u_RegionOffset", "u_CurrentTime", "u_RegionID")));
            assertEquals(0, vs.inputs().get("a_Position"));
            if (mac) { Mtl.release(Mtl.newLibrary(vs.msl())); Mtl.release(Mtl.newLibrary(fs.msl())); }
        }
    }

    private static String source(String path, String defines) throws IOException {
        String s = read(path);
        var matches = Pattern.compile("#moj_import <sodium:([^>]+)>").matcher(s);
        StringBuilder resolved = new StringBuilder();
        while (matches.find()) matches.appendReplacement(resolved, java.util.regex.Matcher.quoteReplacement(source(matches.group(1), "")));
        matches.appendTail(resolved);
        return resolved.toString().replace("#version 330 core", "#version 330 core\n" + defines);
    }
    private static String read(String path) throws IOException {
        try (var in = SodiumShaderTest.class.getResourceAsStream("/assets/sodium/shaders/" + path)) {
            if (in == null) throw new IOException("Missing Sodium shader asset " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
