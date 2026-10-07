// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.fixture;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.eviemod.metal.device.MetalDevice;
import dev.eviemod.metal.device.MetalRenderPass;
import dev.eviemod.metal.mtl.Mtl;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

/** Fixture-only references to SUMR live in a nested class so absent-mod launches also test class loading. */
public final class SumrProbe {
    private static boolean checked;
    private SumrProbe() {}

    public static boolean prepare() {
        String mode = System.getProperty("eviemod.metal.sumrSmoke");
        if (!checked) {
            boolean adapted = Arrays.stream(MetalDevice.class.getInterfaces()).anyMatch(type -> type.getName().equals(
                    "games.enchanted.eg_stop_unloading_my_shaders.common.duck.GpuDeviceAdditions"));
            require(adapted == mode.equals("enabled"), "Unexpected SUMR adapter activation: " + mode);
            checked = true;
            if (!adapted) {
                System.out.println("EVIEMETAL_SUMR_GUARD_OK mode=" + mode);
                return true;
            }
            Enabled.verifyAndReload();
        }
        return !mode.equals("enabled") || Enabled.reloadFinished();
    }

    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static final class Enabled {
        private static boolean finished;
        static void verifyAndReload() {
            require(games.enchanted.eg_stop_unloading_my_shaders.common.ModConstants.isBackendHandled(), "SUMR rejects Metal");
            var device = (MetalDevice) ((games.enchanted.eg_stop_unloading_my_shaders.common.mixin.accessor.GpuDeviceAccessor)
                    RenderSystem.getDevice()).eg_sumr$getBackend();
            var additions = (games.enchanted.eg_stop_unloading_my_shaders.common.duck.GpuDeviceAdditions) device;
            var gui = device.precompilePipeline(RenderPipelines.GUI, null);
            additions.eg_sumr$setBypassPipelineCache(true);
            try {
                var replacement = device.precompilePipeline(RenderPipelines.GUI, (id, type) -> "#version 330\nsyntax error");
                require(replacement.isValid() && replacement != gui && !gui.isValid(), "Vanilla fallback/cache replacement failed");
            } finally { additions.eg_sumr$setBypassPipelineCache(false); }
            ShaderSource green = (id, type) -> type == ShaderType.VERTEX
                    ? "#version 330\nin vec3 Position;void main(){gl_Position=vec4(Position,1.0);}"
                    : "#version 330\nout vec4 color;void main(){color=vec4(0,1,0,1);}";
            for (ShaderType failing : ShaderType.values()) {
                var id = Identifier.fromNamespaceAndPath("eviemetal_fixture", "sumr_" + failing.getName());
                var pipeline = RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
                        .withVertexBinding(0, DefaultVertexFormat.POSITION).withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                        .withCull(false).build();
                require(device.precompilePipeline(pipeline, (shader, type) -> type == failing
                        ? "#version 330\nsyntax error" : green.get(shader, type)).isValid(), "Mod shader fallback failed");
                verifyPixel(device, pipeline, false);
                additions.eg_sumr$setBypassPipelineCache(true);
                try { require(device.precompilePipeline(pipeline, green).isValid(), "Recovery compilation failed"); }
                finally { additions.eg_sumr$setBypassPipelineCache(false); }
                verifyPixel(device, pipeline, true);
            }
            try {
                var errors = games.enchanted.eg_stop_unloading_my_shaders.common.ShaderReloadManager.class
                        .getDeclaredField("knownErrorsThisReload");
                errors.setAccessible(true);
                require(!((List<?>) errors.get(null)).isEmpty(), "SUMR did not receive Metal diagnostics");
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            games.enchanted.eg_stop_unloading_my_shaders.common.ShaderReloadManager.triggerReload();
        }

        private static void verifyPixel(MetalDevice device, RenderPipeline pipeline, boolean visible) {
            var vertices = MemoryUtil.memAlloc(36);
            vertices.putFloat(-1).putFloat(-1).putFloat(0).putFloat(3).putFloat(-1).putFloat(0)
                    .putFloat(-1).putFloat(3).putFloat(0).flip();
            try (var texture = device.createTexture("SUMR pixel", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC,
                    GpuFormat.RGBA8_UNORM, 1, 1, 1, 1);
                 var view = device.createTextureView(texture);
                 var vertexBuffer = device.createBuffer(() -> "SUMR vertices", GpuBuffer.USAGE_VERTEX, vertices);
                 var readback = device.createBuffer(() -> "SUMR readback", GpuBuffer.USAGE_COPY_DST | GpuBuffer.USAGE_MAP_READ, 256)) {
                var encoder = device.createCommandEncoder();
                try (var pass = (MetalRenderPass) encoder.createRenderPass(RenderPassDescriptor.create(() -> "SUMR fallback")
                        .withColorAttachment(view, Optional.of(new Vector4f(1, 0, 0, 1))))) {
                    pass.setPipeline(pipeline);
                    pass.setVertexBuffer(0, vertexBuffer.slice());
                    pass.draw(3, 1, 0, 0);
                }
                encoder.copyTextureToBuffer(texture, readback, 0, () -> {}, 0);
                require(Mtl.fenceWait(Mtl.fence(), 5000), "SUMR pixel fence timed out");
                try (var mapped = readback.map(true, false)) {
                    require(Byte.toUnsignedInt(mapped.data().get(0)) == (visible ? 0 : 255)
                            && Byte.toUnsignedInt(mapped.data().get(1)) == (visible ? 255 : 0), "Wrong SUMR fallback/recovery pixel");
                }
                Mtl.checkError();
            } finally { MemoryUtil.memFree(vertices); }
        }

        static boolean reloadFinished() {
            if (finished) return true;
            try {
                var field = games.enchanted.eg_stop_unloading_my_shaders.common.ShaderReloadManager.class.getDeclaredField("isHotReloading");
                field.setAccessible(true);
                if (field.getBoolean(null)) return false;
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            finished = true;
            System.out.println("EVIEMETAL_SUMR_RECOVERY_OK vanilla/modded/cache/pixels/diagnostics/hot-reload");
            return true;
        }
    }
}
