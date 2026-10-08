// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuSurface;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.textures.GpuTexture;
import dev.eviemod.metal.NativeLoader;
import dev.eviemod.metal.mtl.Mtl;
import dev.eviemod.metal.shader.ShaderTranslator;
import java.util.Map;
import java.util.Optional;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

/** Physical first-thread checkpoint, loading the native bridge from the distributable JAR. */
public final class SurfaceSmoke {
    public static void main(String[] args) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("GLFW startup failed");
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        long window = GLFW.glfwCreateWindow(320, 240, "Eviemetal surface fixture", 0, 0);
        if (window == 0) throw new IllegalStateException("Surface fixture window creation failed");
        String addon = dev.eviemod.metal.EvieMetal.class.getProtectionDomain().getCodeSource().getLocation().toString();
        if (!addon.endsWith(".jar")) throw new AssertionError("Surface fixture requires the distribution JAR: " + addon);
        for (Class<?> tool : new Class<?>[]{org.lwjgl.util.shaderc.Shaderc.class, org.lwjgl.util.spvc.Spvc.class}) {
            String source = tool.getProtectionDomain().getCodeSource().getLocation().toString();
            if (!source.contains("/packagedSmoke/surface-tools/") || !source.endsWith(".jar"))
                throw new AssertionError("Surface shader tool must be extracted from the packaged nested JAR: " + source);
            System.out.println("EVIEMETAL_SURFACE_SHADER_TOOL_SOURCE " + source);
        }
        NativeLoader.load();
        var device = new MetalDevice(window, (id, type) -> null);
        try {
            var translated = ShaderTranslator.translate("surface-preflight", "#version 330\nvoid main(){gl_Position=vec4(0,0,0,1);}", ShaderTranslator.Stage.VERTEX, Map.of());
            Mtl.release(Mtl.newLibrary(translated.msl()));
            device.prepareSurface(window);
            var backend = device.createCommandEncoder();
            var encoder = new CommandEncoder(null, device, backend);
            try (var surface = new GpuSurface(device.createSurface(window));
                 var color = device.createTexture("surface-clear", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_SRC, GpuFormat.RGBA8_UNORM, 64, 64, 1, 1);
                 var view = device.createTextureView(color)) {
                for (var mode : new GpuSurface.PresentMode[]{GpuSurface.PresentMode.FIFO, GpuSurface.PresentMode.IMMEDIATE}) {
                    surface.configure(new GpuSurface.Configuration(320, 240, mode));
                    surface.acquireNextTexture();
                    backend.createRenderPass(RenderPassDescriptor.create(() -> "surface-clear").withColorAttachment(view, Optional.of(new Vector4f(0, 1, 0, 1))));
                    backend.submitRenderPass();
                    surface.blitFromTexture(encoder, view);
                    encoder.submit();
                    surface.present();
                }
                try (var readback = new MetalBuffer(0, 256)) {
                    Mtl.copyTextureToBuffer(((MetalTexture) color).handle, 0, 0, 0, 1, 1, readback.handle, 0, 256);
                    if (!Mtl.fenceWait(Mtl.fence(), 5000)) throw new AssertionError("Surface completion timed out");
                    for (int i = 0; i < 4; i++) {
                        int expected = i == 1 || i == 3 ? 255 : 0;
                        if (Byte.toUnsignedInt(MemoryUtil.memGetByte(readback.address() + i)) != expected) throw new AssertionError("Surface clear readback mismatch");
                    }
                }
                Mtl.checkError();
            }
            System.out.println("EVIEMETAL_SURFACE_OK mc=26.2 backend=" + device.getBackendName());
        } finally {
            device.close();
            if (Mtl.hasLiveContext() || Mtl.allocatedBytes() != 0 || Mtl.completedFence() != 0)
                throw new AssertionError("Surface fixture left native context alive");
            System.out.println("EVIEMETAL_SURFACE_CLOSED_OK");
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }
}
