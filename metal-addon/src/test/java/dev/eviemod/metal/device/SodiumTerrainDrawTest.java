package dev.eviemod.metal.device;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.compat.sodium.SodiumMetal;
import dev.eviemod.metal.mtl.Mtl;
import dev.eviemod.metal.shader.ShaderTranslator;
import net.minecraft.resources.Identifier;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.MemoryStack;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.MAC)
class SodiumTerrainDrawTest {
    @Test void nativeEncoderGenerationDistinguishesMergesFromUploadSplits() {
        NativeLoader.load();
        var device = new MetalDevice(0, (id, type) -> "");
        long buffer = Mtl.newBuffer(256);
        try (var texture = (MetalTexture) device.createTexture("generation target",
                GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, TextureFormat.RGBA8, 1, 1, 1, 1)) {
            Mtl.beginPass(texture.handle, true, 0, 0, 0, 1, 0, false, 1);
            long first = Mtl.renderEncoderGeneration();
            assertTrue(first > 0);
            Mtl.endPass();
            Mtl.beginPass(texture.handle, false, 0, 0, 0, 1, 0, false, 1);
            assertEquals(first, Mtl.renderEncoderGeneration(), "Logical passes can reuse the native encoder");
            Mtl.endPass();
            Mtl.copyTextureToBuffer(texture.handle, 0, 0, 0, 1, 1, buffer, 0, 256);
            assertEquals(0, Mtl.renderEncoderGeneration());
            Mtl.beginPass(texture.handle, false, 0, 0, 0, 1, 0, false, 1);
            assertTrue(Mtl.renderEncoderGeneration() > first);
            Mtl.endPass();
            assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
            Mtl.checkError();
        } finally {
            Mtl.release(buffer);
            device.close();
        }
    }

    @Test void multiDrawOffsetsBaseVerticesAndSignedTimesFollowOrphanedStorage() {
        NativeLoader.load();
        var device = new MetalDevice(0, (id, type) -> type == ShaderType.VERTEX
                ? "#version 330\nin vec3 Position; void main(){gl_Position=vec4(Position,1);}"
                : "#version 330\nuniform isamplerBuffer Times; out vec4 color; void main(){color=texelFetch(Times,1).r == -1234567 ? vec4(0,1,0,1) : vec4(1,0,0,1);}");
        long readback = Mtl.newBuffer(256);
        int timeView = 0;
        var vertices = MemoryUtil.memCalloc(72);
        var indices = MemoryUtil.memCalloc(24);
        try (var times = new MetalBuffer(GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, 16)) {
            vertices.position(36);
            vertices.putFloat(-1).putFloat(-1).putFloat(0).putFloat(3).putFloat(-1).putFloat(0).putFloat(-1).putFloat(3).putFloat(0).flip();
            indices.position(12); indices.putInt(0).putInt(1).putInt(2).flip();
            timeView = SodiumMetal.createTimeView(times);
            var id = Identifier.fromNamespaceAndPath("eviemod_metal", "sodium_native_regression");
            var info = RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
                    .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
                    .withCull(false).withDepthStencilState(Optional.empty()).build();
            var pipeline = (MetalPipeline) device.precompilePipeline(info, null);
            try (var vb = (MetalBuffer) device.createBuffer(() -> "vertices", GpuBuffer.USAGE_VERTEX, vertices);
                 var ib = (MetalBuffer) device.createBuffer(() -> "indices", GpuBuffer.USAGE_INDEX, indices);
                 var texture = (MetalTexture) device.createTexture("target", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                         TextureFormat.RGBA8, 1, 1, 1, 1);
                 var stack = MemoryStack.stackPush()) {
                var counts = stack.ints(0, 3);
                var offsets = stack.pointers(0L, 12L);
                var bases = stack.ints(0, 3);
                var value = stack.ints(-1234567);
                long baseline = 0;
                for (int i = 0; i < 40; i++) {
                    long generation = MetalTerrainResources.storageGeneration(times);
                    assertTrue(times.tryOrphanWrite(4, MemoryUtil.memAddress(value), 4));
                    assertEquals(generation + 1, MetalTerrainResources.storageGeneration(times));
                    long view = SodiumMetal.bindTimeView(2, timeView);
                    Mtl.beginPass(texture.handle, true, 0, 0, 0, 1, 0, false, 1);
                    Mtl.setPipelineState(pipeline.state(0, -1), pipeline.depthState, Mtl.CULL_NONE, false, 0, 0);
                    Mtl.setBuffer(false, ShaderTranslator.VERTEX_BUFFER_INDEX, vb.handle, 0);
                    Mtl.setTexture(true, pipeline.fragment.textures().get("Times"), view, -1, 0);
                    Mtl.multiDrawIndexed(Mtl.PRIMITIVE_TRIANGLE, true, ib.handle, MemoryUtil.memAddress(counts), offsets.address(), MemoryUtil.memAddress(bases), 2);
                    Mtl.endPass();
                    Mtl.copyTextureToBuffer(texture.handle, 0, 0, 0, 1, 1, readback, 0, 256);
                    assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
                    long pixels = Mtl.bufferContents(readback);
                    assertEquals(0, Byte.toUnsignedInt(MemoryUtil.memGetByte(pixels)));
                    assertEquals(255, Byte.toUnsignedInt(MemoryUtil.memGetByte(pixels + 1)));
                    if (i == 8) baseline = Mtl.allocatedBytes();
                }
                assertTrue(Mtl.allocatedBytes() - baseline < 1 << 20, "Completed orphaned timestamp views must retire");
                Mtl.checkError();
            }
        } finally {
            SodiumMetal.deleteTimeView(timeView);
            SodiumMetal.endPass();
            MemoryUtil.memFree(vertices); MemoryUtil.memFree(indices);
            Mtl.release(readback); device.close();
        }
    }
}
