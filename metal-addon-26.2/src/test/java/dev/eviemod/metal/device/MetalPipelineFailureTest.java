// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import java.util.Optional;
import org.joml.Vector4f;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.mtl.Mtl;
import java.util.OptionalInt;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.lwjgl.system.MemoryUtil;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.MAC)
class MetalPipelineFailureTest {
    @Test void failedCompilationClosesPassAndPreservesDiagnostic() {
        NativeLoader.load();
        for (ShaderType failingStage : ShaderType.values()) {
            var device = new MetalDevice(0, (id, type) -> {
                if (id.getPath().equals("bad") && type == failingStage) return "#version 150\nvoid main() { unexpected };";
                return type == ShaderType.VERTEX
                        ? "#version 150\nin vec3 Position;void main(){gl_Position=vec4(Position,1);}"
                        : "#version 150\nout vec4 color;void main(){color=vec4(1);}";
            });
            try (var texture = device.createTexture("target", GpuTexture.USAGE_RENDER_ATTACHMENT, GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
                 var view = device.createTextureView(texture);
                 var vertices = new MetalBuffer(GpuBuffer.USAGE_VERTEX, 36);
                 var readback = new MetalBuffer(GpuBuffer.USAGE_COPY_DST, 256)) {
                float[] positions = {-1, -1, 0, 3, -1, 0, -1, 3, 0};
                for (int i = 0; i < positions.length; i++) MemoryUtil.memPutFloat(vertices.address() + i * 4L, positions[i]);
                var backend = (MetalCommandEncoder) device.createCommandEncoder();
                var encoder = backend;
                var bad = pipeline("fixture:broken", "fixture:bad");
                var failedPass = (MetalRenderPass) encoder.createRenderPass(RenderPassDescriptor.create(() -> "bad").withColorAttachment(view, Optional.of(new Vector4f())));
                failedPass.pushDebugGroup(() -> "open at failure");
                var error = assertThrows(IllegalStateException.class, () -> failedPass.setPipeline(bad));
                assertTrue(error.getMessage().contains("pipeline fixture:broken"), error.getMessage());
                assertTrue(error.getMessage().contains("fixture:bad"), error.getMessage());
                assertTrue(error.getMessage().contains(failingStage == ShaderType.VERTEX ? "[vertex]" : "[fragment]"), error.getMessage());
                assertNotNull(error.getCause());
                assertFalse(backend.isInRenderPass());
                try (var next = (MetalRenderPass) encoder.createRenderPass(RenderPassDescriptor.create(() -> "recovery").withColorAttachment(view))) {
                    // Late cleanup must not end the new pass, and the old pipeline must not be reused.
                    failedPass.close();
                    assertTrue(backend.isInRenderPass());
                    assertThrows(IllegalStateException.class, () -> failedPass.setPipeline(pipeline("fixture:good", "fixture:good")));
                    next.setPipeline(pipeline("fixture:good", "fixture:good"));
                    next.setVertexBuffer(0, vertices.slice());
                    next.draw(3, 1, 0, 0);
                }
                assertFalse(backend.isInRenderPass());
                Mtl.copyTextureToBuffer(((MetalTexture) texture).handle, 0, 0, 0, 1, 1, readback.handle, 0, 256);
                assertTrue(Mtl.fenceWait(Mtl.fence(), 5000));
                for (int i = 0; i < 4; i++) assertEquals(255, Byte.toUnsignedInt(MemoryUtil.memGetByte(readback.address() + i)));
                Mtl.checkError();
            } finally { device.close(); }
        }
    }

    private static RenderPipeline pipeline(String id, String shader) {
        return RenderPipeline.builder().withLocation(Identifier.parse(id)).withVertexShader(Identifier.parse(shader)).withFragmentShader(Identifier.parse(shader))
                .withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).withCull(false).build();
    }
}
