package dev.eviemod.compat;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Desktop file-picker boundary for the GLFW client. */
public final class FilePicker {
    private FilePicker() {}
    public static CompletableFuture<Path> choose(String title, String extension, String description) {
        try (var memory = MemoryStack.stackPush()) {
            String chosen = TinyFileDialogs.tinyfd_openFileDialog(title, "",
                memory.pointers(memory.UTF8("*." + extension)), description, false);
            return CompletableFuture.completedFuture(chosen == null ? null : Path.of(chosen));
        } catch (RuntimeException e) { return CompletableFuture.failedFuture(e); }
    }
}
