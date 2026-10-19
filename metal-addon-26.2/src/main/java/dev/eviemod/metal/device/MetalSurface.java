// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal.device;

import com.mojang.blaze3d.systems.*;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.eviemod.metal.mtl.Mtl;
import java.util.Collection;
import java.util.Set;
import org.lwjgl.glfw.GLFWNativeCocoa;

/** 26.2 surface ownership; constructed after preflight while vanilla can still retry startup. */
final class MetalSurface implements GpuSurfaceBackend {
    private boolean closed;
    MetalSurface(long window) { Mtl.attach(GLFWNativeCocoa.glfwGetCocoaWindow(window)); }
    private void checkOpen() { if (closed) throw new IllegalStateException("Metal surface is closed"); }
    @Override public void configure(GpuSurface.Configuration config) throws SurfaceException {
        checkOpen();
        if (config.width() < 1 || config.height() < 1) throw new SurfaceException("Invalid Metal surface dimensions");
        Mtl.configureSurface(config.width(), config.height(), config.presentMode() == GpuSurface.PresentMode.FIFO);
    }
    @Override public boolean isSuboptimal() { return false; }
    @Override public void acquireNextTexture() { checkOpen(); Mtl.acquireSurface(); }
    @Override public void blitFromTexture(CommandEncoderBackend encoder, GpuTextureView texture) {
        checkOpen();
        if (!(encoder instanceof MetalCommandEncoder)) throw new IllegalArgumentException("Metal surface requires a Metal command encoder");
        Mtl.blitSurface(((MetalTextureView) texture).handle);
    }
    @Override public void present() { checkOpen(); Mtl.presentSurface(); Mtl.checkError(); UploadRing.trimCompleted(); }
    @Override public Collection<GpuSurface.PresentMode> supportedPresentModes() { return Set.of(GpuSurface.PresentMode.FIFO, GpuSurface.PresentMode.IMMEDIATE); }
    @Override public void close() { if (closed) return; closed = true; Mtl.closeSurface(); }
}
