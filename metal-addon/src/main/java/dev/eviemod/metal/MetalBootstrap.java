// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal;

import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.GpuDevice;
import dev.eviemod.metal.device.MetalDevice;
import dev.eviemod.metal.mtl.Mtl;
import dev.eviemod.metal.shader.ShaderTranslator;
import java.util.Map;
import java.util.stream.Collectors;
import net.fabricmc.loader.api.FabricLoader;

public final class MetalBootstrap {
    private MetalBootstrap() {}

    /** Null means leave device creation entirely to vanilla. No native access before policy checks. */
    public static GpuDevice tryCreate(long window, ShaderSource shaders) {
        var loader = FabricLoader.getInstance();
        String reason = MetalSupport.unavailableReason(Boolean.getBoolean("eviemod.metal"),
                System.getProperty("os.name", ""), System.getProperty("os.arch", ""),
                System.getProperty("os.version", ""),
                loader.getAllMods().stream().map(m -> m.getMetadata().getId()).collect(Collectors.toSet()));
        if (reason != null) {
            EvieMetal.LOGGER.info("Using OpenGL: {}", reason);
            return null;
        }
        boolean nativeLoaded = false;
        MetalDevice device = null;
        try {
            NativeLoader.load();
            nativeLoaded = true;
            ShaderTranslator.enableCache(loader.getGameDir().resolve("eviemod-metal/shader-cache"));
            device = new MetalDevice(window, shaders);
            // Exercise both translator natives and Apple's compiler before touching the Cocoa view.
            var test = ShaderTranslator.translate("startup", "#version 330\nvoid main(){gl_Position=vec4(0,0,0,1);}",
                    ShaderTranslator.Stage.VERTEX, Map.of());
            Mtl.release(Mtl.newLibrary(test.msl()));
            device.attach(window);
            EvieMetal.LOGGER.info("Experimental Metal backend active: {}", device.getRenderer());
            return new GpuDevice(device);
        } catch (Exception | LinkageError e) {
            EvieMetal.LOGGER.error("Metal startup failed; falling back to OpenGL", e);
            try {
                if (device != null) device.close();
                else if (nativeLoaded) Mtl.shutdown();
            } catch (Exception | LinkageError cleanup) { e.addSuppressed(cleanup); }
            return null;
        }
    }
}
