package dev.eviemod.paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

final class ConfigMigration {
    static Path path(String name) {
        Path directory = FabricLoader.getInstance().getConfigDir();
        try { return migrate(directory, name); }
        catch (IOException e) { throw new IllegalStateException("Could not migrate " + name + "; old file preserved", e); }
    }
    static Path migrate(Path directory, String name) throws IOException {
        Path target = directory.resolve(name);
        Path legacy = directory.resolve(name.replace("eviemod-", "skyshitter-"));
        // Copy once; preserve the original and never overwrite a newer eviemod configuration.
        if (!Files.exists(target) && Files.exists(legacy)) Files.copy(legacy, target);
        return target;
    }
}
