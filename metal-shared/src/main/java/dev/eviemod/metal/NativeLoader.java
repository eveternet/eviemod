// SPDX-License-Identifier: GPL-3.0-only
package dev.eviemod.metal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NativeLoader {
    private static boolean loaded;
    private NativeLoader() {}

    public static synchronized void load() {
        if (loaded) return;
        try (InputStream in = NativeLoader.class.getResourceAsStream("/natives/libeviemod_metal.dylib")) {
            if (in == null) throw new IllegalStateException("Metal native missing; use the macOS distribution JAR");
            Path lib = Files.createTempFile("eviemod-metal-", ".dylib");
            lib.toFile().deleteOnExit();
            Files.copy(in, lib, StandardCopyOption.REPLACE_EXISTING);
            System.load(lib.toAbsolutePath().toString());
            loaded = true;
        } catch (IOException e) { throw new IllegalStateException("Could not extract Metal native", e); }
    }
}
