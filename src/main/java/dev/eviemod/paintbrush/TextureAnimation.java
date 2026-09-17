package dev.eviemod.paintbrush;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;

/** Validate animation layout before handing an imported sprite to Minecraft's atlas loader. */
final class TextureAnimation {
    static String validate(String metadata, int width, int height) throws IOException {
        if (metadata == null) return null;
        try {
            if (metadata.length() > 65536) throw new IllegalArgumentException("Metadata is too large");
            var root = JsonParser.parseString(metadata).getAsJsonObject();
            if (!root.has("animation") || !root.get("animation").isJsonObject()) throw new IllegalArgumentException("Missing animation section");
            var animation = AnimationMetadataSection.CODEC.parse(JsonOps.INSTANCE, root.get("animation")).getOrThrow();
            var size = animation.calculateFrameSize(width, height);
            if (size.width() < 1 || size.height() < 1 || width % size.width() != 0 || height % size.height() != 0)
                throw new IllegalArgumentException("Frame dimensions must divide the PNG dimensions");
            int count = (width / size.width()) * (height / size.height());
            if (animation.frames().isPresent()) {
                var frames = animation.frames().get();
                if (frames.isEmpty()) throw new IllegalArgumentException("Animation has no frames");
                for (var frame : frames) if (frame.index() < 0 || frame.index() >= count || frame.timeOr(animation.defaultFrameTime()) < 1)
                    throw new IllegalArgumentException("Invalid frame index or duration");
            }
            var clean = new JsonObject(); clean.add("animation", AnimationMetadataSection.CODEC.encodeStart(JsonOps.INSTANCE, animation).getOrThrow());
            return clean.toString();
        } catch (RuntimeException e) { throw new IOException("Invalid animation metadata: " + e.getMessage(), e); }
    }
}
