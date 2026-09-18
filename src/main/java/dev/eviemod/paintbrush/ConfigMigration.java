package dev.eviemod.paintbrush;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

final class ConfigMigration {
    private record Source(Path directory, String name) {}
    private static final java.util.Map<Path, Source> SOURCES = new java.util.concurrent.ConcurrentHashMap<>();
    static Path path(String name) {
        Path directory = FabricLoader.getInstance().getConfigDir();
        Path target = target(directory, name);
        SOURCES.put(target, new Source(directory, name));
        return target;
    }
    // Persistence boundaries call this inside their existing IOException handling, never static initialization.
    static void prepare(Path target) throws IOException {
        Source source = SOURCES.get(target);
        if (source != null) migrate(source.directory(), source.name());
    }
    private static Path target(Path directory, String name) {
        return directory.resolve("eviemod").resolve(name.equals("eviemod.json") ? "settings.json" : name.replace("eviemod-", ""));
    }
    static Path migrate(Path directory, String name) throws IOException {
        Path target = target(directory, name);
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
