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
        Path target = directory.resolve("eviemod").resolve(name.equals("eviemod.json") ? "settings.json" : name.replace("eviemod-", ""));
        Path previous = directory.resolve(name);
        Path legacy = Files.exists(previous) ? previous : directory.resolve(name.replace("eviemod-", "skyshitter-"));
        // Copy atomically, once. Both generations remain untouched as backups.
        if (!Files.exists(target) && Files.exists(legacy)) {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "migration-", ".tmp");
            try {
                Files.copy(legacy, temporary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } finally { Files.deleteIfExists(temporary); }
        }
        return target;
    }
}
