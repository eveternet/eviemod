// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal;

import com.mojang.blaze3d.GLFWErrorCapture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import org.lwjgl.glfw.GLFW;

/** Uses vanilla's ordered backend retry loop, including closing a failed Metal window before trying OpenGL. */
final class MetalBackend implements GpuBackend {
    @Override public String getName() { return "Metal (experimental)"; }

    @Override public void setWindowHints() {
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
    }

    @Override public void handleWindowCreationErrors(GLFWErrorCapture.Error error) throws BackendCreationException {
        throw new BackendCreationException("Metal window creation failed: " + (error == null ? "unknown GLFW error" : error));
    }

    @Override public GpuDevice createDevice(long window, ShaderSource shaders, GpuDebugOptions debug) {
        GpuDevice device = MetalBootstrap.tryCreate(window, shaders);
        if (device == null) throw MetalBackend.<RuntimeException>creationFailed();
        return device;
    }

    // GpuBackend.createDevice omits a throws declaration, but Minecraft's surrounding retry loop catches
    // BackendCreationException. Preserve that exact failure type rather than skipping vanilla's cleanup/retry.
    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException creationFailed() throws T {
        throw (T) new BackendCreationException("Metal startup failed; see the log for the cause. Trying OpenGL.");
    }
}
