package dev.eviemod.compat;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.sdl.SDLError;
import static org.lwjgl.sdl.SDLDialog.*;
import static org.lwjgl.sdl.SDLProperties.*;
import static org.lwjgl.system.MemoryUtil.*;

/** SDL requires main-thread launch and filter memory alive through the asynchronous callback. */
public final class FilePicker {
    private FilePicker() {}
    public static CompletableFuture<Path> choose(String title, String extension, String description) {
        var result = new CompletableFuture<Path>();
        var name = memUTF8(description);
        var pattern = memUTF8(extension);
        var filters = SDL_DialogFileFilter.calloc(1);
        filters.get(0).name(name).pattern(pattern);
        int properties = SDL_CreateProperties();
        var callback = new SDL_DialogFileCallback() {
            @Override public void invoke(long userdata, long files, int filter) {
                try {
                    if (files == NULL) result.completeExceptionally(new IllegalStateException(SDLError.SDL_GetError()));
                    else {
                        long first = memGetAddress(files);
                        result.complete(first == NULL ? null : Path.of(memUTF8(first)));
                    }
                } catch (RuntimeException e) { result.completeExceptionally(e); }
                finally {
                    SDL_DestroyProperties(properties);
                    filters.free(); memFree(name); memFree(pattern); free();
                }
            }
        };
        SDL_SetStringProperty(properties, SDL_PROP_FILE_DIALOG_TITLE_STRING, title);
        SDL_SetPointerProperty(properties, SDL_PROP_FILE_DIALOG_WINDOW_POINTER, Minecraft.getInstance().getWindow().handle());
        SDL_SetPointerProperty(properties, SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filters.address());
        SDL_SetNumberProperty(properties, SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1);
        SDL_ShowFileDialogWithProperties(SDL_FILEDIALOG_OPENFILE, callback, NULL, properties);
        return result;
    }
}
