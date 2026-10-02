package dev.eviemod.metal.device;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.mtl.Mtl;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.MAC)
class MetalDrawTest {
    @Test void backendRendersIndexedTriangleWithBaseVertex() {
        NativeLoader.load();
        var device = new MetalDevice(0, (id, type) -> type == ShaderType.VERTEX
                ? "#version 330\nin vec3 Position; void main(){gl_Position=vec4(Position,1);}"
                : "#version 330\nout vec4 color; void main(){color=vec4(0,1,0,1);}");
        long readback = 0;
        ByteBuffer vertices = MemoryUtil.memAlloc(72), indices = MemoryUtil.memAlloc(12);
        try {
            // First three vertices are degenerate. The Draw baseVertex must select the final three.
            for (int i = 0; i < 9; i++) vertices.putFloat(0);
            vertices.putFloat(-1).putFloat(-1).putFloat(0);
            vertices.putFloat(3).putFloat(-1).putFloat(0);
            vertices.putFloat(-1).putFloat(3).putFloat(0);
            vertices.flip();
            indices.putInt(0).putInt(1).putInt(2).flip();
            var pipeline = RenderPipeline.builder().withLocation("eviemod_metal:smoke")
                    .withVertexShader("eviemod_metal:smoke").withFragmentShader("eviemod_metal:smoke")
                    .withVertexFormat(DefaultVertexFormat.POSITION, VertexFormat.Mode.TRIANGLES)
                    .withCull(false).withDepthStencilState(Optional.empty()).build();
            try (var vb = device.createBuffer(() -> "vertices", GpuBuffer.USAGE_VERTEX, vertices);
                 var ib = device.createBuffer(() -> "indices", GpuBuffer.USAGE_INDEX, indices);
                 var texture = device.createTexture("smoke", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                         TextureFormat.RGBA8, 1, 1, 1, 1);
                 var view = device.createTextureView(texture)) {
                try (var pass = device.createCommandEncoder().createRenderPass(() -> "smoke", view, OptionalInt.of(0xFF000000))) {
                    pass.setPipeline(pipeline);
                    pass.drawMultipleIndexed(List.of(new RenderPass.Draw<Void>(0, vb, ib, VertexFormat.IndexType.INT, 0, 3, 3)),
                            null, null, List.of(), null);
                }
                readback = Mtl.newBuffer(256);
                assertNotEquals(0, readback);
                Mtl.copyTextureToBuffer(((MetalTexture) texture).handle, 0, 0, 0, 1, 1, readback, 0, 256);
                assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
                long address = Mtl.bufferContents(readback);
                assertEquals(0, MemoryUtil.memGetByte(address));
                assertEquals(255, Byte.toUnsignedInt(MemoryUtil.memGetByte(address + 1)));
                assertEquals(0, MemoryUtil.memGetByte(address + 2));
                assertEquals(255, Byte.toUnsignedInt(MemoryUtil.memGetByte(address + 3)));
                Mtl.checkError();
            }
        } finally {
            MemoryUtil.memFree(vertices); MemoryUtil.memFree(indices);
            Mtl.release(readback);
            device.close();
        }
    }
}
