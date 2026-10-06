// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal;

import com.mojang.blaze3d.GLFWErrorCapture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import org.lwjgl.glfw.GLFW;

/** Uses vanilla's ordered backend retry loop, including closing a failed Metal window before retrying the preferred vanilla backend. */
final class MetalBackend implements GpuBackend {
    @Override public String getName() { return "Metal (experimental)"; }

    @Override public void setWindowHints() {
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
    }

    @Override public void handleWindowCreationErrors(GLFWErrorCapture.Error error) throws BackendCreationException {
        throw new BackendCreationException("Metal window creation failed: " + (error == null ? "unknown GLFW error" : error));
    }

    @Override public GpuDevice createDevice(long window, ShaderSource shaders, GpuDebugOptions debug, Runnable criticalShaderLoader) throws BackendCreationException {
        GpuDevice device = MetalBootstrap.tryCreate(window, shaders, criticalShaderLoader);
        if (device == null) throw new BackendCreationException("Metal startup failed; see the log for the cause. Retrying Minecraft\'s preferred backends.");
        return device;
    }

}
