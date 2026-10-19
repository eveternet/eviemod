// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.shader;

import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.mtl.Mtl;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class GlslCompatibilityTest {
    @ParameterizedTest
    @ValueSource(strings = {"130", "140", "150", "150 core", "330", "330 core"})
    void explicitInterfacesAndUniformBlocks(String version) throws Exception {
        // Synthetic inventory-style shader: lower-version fragment + explicit vertex interfaces,
        // std140 integer settings, texture arithmetic and discard, with deliberately reordered varyings.
        var vs = translate("fixture:inventory.vsh", """
                #version %s
                in vec3 Position; in vec2 UV0; in vec4 Color;
                layout(std140) uniform Projection { mat4 ProjMat; };
                out vec4 vertexColor; out vec2 texCoord0;
                void main() { gl_Position = ProjMat * vec4(Position, 1); vertexColor = Color; texCoord0 = UV0; }
                """.formatted(version), ShaderTranslator.Stage.VERTEX, Map.of());
        var fs = translate("fixture:inventory.fsh", """
                #version %s
                uniform sampler2D Sampler0;
                layout(std140) uniform InventorySettings { ivec2 Size; };
                in vec2 texCoord0; in vec4 vertexColor; out vec4 fragColor;
                void main() {
                    vec2 uv = mod(texCoord0 * vec2(Size), vec2(1));
                    vec4 color = texture(Sampler0, uv);
                    if (color.a < 0.1) discard;
                    fragColor = color * vec4(vertexColor.rgb, 1);
                }
                """.formatted(version), ShaderTranslator.Stage.FRAGMENT, vs.outputs());
        assertEquals(Set.of("Position", "UV0", "Color"), vs.inputs().keySet());
        assertTrue(vs.buffers().containsKey("Projection"));
        assertTrue(fs.buffers().containsKey("InventorySettings"));
        assertTrue(fs.textures().containsKey("Sampler0"));
        assertEquals(vs.outputs(), fs.inputs());
        assertTrue(fs.defaults().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "#version 110\n", "#version 120\n", "#version 150\n"})
    void legacyAttributesVaryingsSamplingAndFragmentColor(String header) throws Exception {
        var vs = translate("legacy.vsh", header + """
                attribute vec3 Position; attribute vec2 UV0;
                varying vec2 uv;
                void main() { gl_Position = vec4(Position, 1); uv = UV0; }
                """, ShaderTranslator.Stage.VERTEX, Map.of());
        var fs = translate("legacy.fsh", header + """
                uniform sampler2D Sampler0;
                varying vec2 uv;
                void main() { gl_FragColor = texture2D(Sampler0, uv); }
                """, ShaderTranslator.Stage.FRAGMENT, vs.outputs());
        assertEquals(vs.outputs(), fs.inputs());
        assertEquals(0, fs.outputs().values().iterator().next());
        assertTrue(fs.textures().containsKey("Sampler0"));
    }

    @Test void macrosVersionConditionalsCommentsAndLineMappings() throws Exception {
        String source = """
                  /* #version 999 */ # version 150 core
                #define DECLARE(T, N) uniform T N;
                #if defined(__VERSION__) && __VERSION__ == 150
                DECLARE(vec4, Tint)
                #else
                uniform UndefinedType WrongBranch;
                #endif
                #if 0
                uniform float Ignored = 1;
                #endif
                // uniform float Comment; gl_FragColor texture2D
                layout(std140) uniform Settings { uniform float Factor; };
                out vec4 fragColor;
                #line 90 7
                void main() { fragColor = Tint * Factor; }
                """.replace("\n", "\r\n");
        var fs = translate("macros.fsh", source, ShaderTranslator.Stage.FRAGMENT, Map.of());
        assertEquals(Set.of("Tint"), fs.defaults().keySet());
        assertTrue(fs.buffers().containsKey("Settings"));
        String prepared = GlslCompatibility.prepareVersion(source);
        assertTrue(prepared.contains("/* #version 999 */"));
        assertTrue(prepared.contains("#define EvieMetalSourceVersion 150"));
        assertTrue(prepared.contains("defined(EvieMetalSourceVersion) && EvieMetalSourceVersion == 150"));
        var error = assertThrows(ShaderTranslator.TranslationException.class,
                () -> ShaderTranslator.translate("fixture:broken.fsh", source.replace("Tint * Factor", "missingSymbol"), ShaderTranslator.Stage.FRAGMENT, Map.of()));
        assertTrue(error.getMessage().contains("fixture:broken.fsh [fragment]"), error.getMessage());
        assertTrue(error.getMessage().contains("7:90"), error.getMessage());
    }

    @Test void blockMembersAndMultipleDeclarationsStayInPlace() throws Exception {
        var fs = translate("uniforms.fsh", """
                #version 150
                layout(std140) uniform Settings {
                    uniform float Factor;
                    uniform vec4 Tint;
                };
                uniform highp vec4 Extra[2], Other; uniform float Amount;
                out vec4 fragColor; void main() { fragColor = (Tint + Extra[1] + Other) * Factor * Amount; }
                """, ShaderTranslator.Stage.FRAGMENT, Map.of());
        assertTrue(fs.buffers().containsKey("Settings"));
        assertEquals(Set.of("Extra", "Other", "Amount"), fs.defaults().keySet());
        assertFalse(fs.defaults().containsKey("Factor"));
        assertEquals(32, fs.defaults().get("Other"));
    }

    @Test void legacyProjectionAndLodSampling() throws Exception {
        var fs = translate("sampling.fsh", """
                #version 120
                uniform sampler2D Sampler0;
                varying vec2 uv;
                void main() {
                    gl_FragColor = texture2DProj(Sampler0, vec3(uv, 1))
                        + texture2DLod(Sampler0, uv, 0.0);
                }
                """, ShaderTranslator.Stage.FRAGMENT, Map.of("uv", 3));
        assertEquals(3, fs.inputs().get("uv"));
    }

    @Test void generatedOutputDoesNotCollideWithUserNames() throws Exception {
        var fs = translate("names.fsh", """
                #version 120
                const vec4 EvieMetalFragColor = vec4(0.25);
                void main() { gl_FragColor = EvieMetalFragColor; }
                """, ShaderTranslator.Stage.FRAGMENT, Map.of());
        assertEquals(Set.of("EvieMetalFragColor_"), fs.outputs().keySet());
    }

    @Test void mslReservedSamplerNameRetainsResourceBinding() throws Exception {
        var fs = translate("reserved.fsh", """
                #version 150
                uniform sampler2D sampler;
                in vec2 uv; out vec4 color;
                vec4 sampleColor(sampler2D source, vec2 pos) { return texture(source, pos); }
                void main() { float sampler_ = 0.5; color = sampleColor(sampler, uv) * sampler_; }
                """, ShaderTranslator.Stage.FRAGMENT, Map.of("uv", 0));
        assertTrue(fs.textures().containsKey("sampler"), fs.textures().toString());
        assertTrue(fs.samplers().containsKey("sampler"), fs.samplers().toString());
    }

    @Test void unsupportedFormsFailWithStageAndUnderlyingReason() {
        for (String source : new String[]{
                "#version 300 es\nvoid main(){}",
                "#version 150 compatibility\nvoid main(){}",
                "#version 120\nvoid main(){gl_FragData[0]=vec4(1);}",
                "#version 120\nvoid main(){gl_FragColor=gl_Color;}",
                "#version 150\nuniform float Value=1;out vec4 color;void main(){color=vec4(Value);}",
                "#version 120\nvec4 texture2D(float x){return vec4(x);}void main(){gl_FragColor=texture2D(1.0);}"
        }) {
            var error = assertThrows(ShaderTranslator.TranslationException.class,
                    () -> ShaderTranslator.translate("fixture:unsupported.fsh", source, ShaderTranslator.Stage.FRAGMENT, Map.of()));
            assertTrue(error.getMessage().contains("fixture:unsupported.fsh [fragment]"), error.getMessage());
        }
    }

    private static ShaderTranslator.Result translate(String name, String source, ShaderTranslator.Stage stage, Map<String, Integer> inputs) throws Exception {
        var result = ShaderTranslator.translate(name, source, stage, inputs);
        if (System.getProperty("os.name").startsWith("Mac")) {
            NativeLoader.load();
            Mtl.release(Mtl.newLibrary(result.msl()));
        }
        return result;
    }
}
