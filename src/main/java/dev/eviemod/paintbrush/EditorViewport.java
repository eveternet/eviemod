package dev.eviemod.paintbrush;

/** Maps Minecraft GUI coordinates into a compact editor coordinate space. */
public record EditorViewport(float scale, int width, int height) {
    public static EditorViewport fit(int guiWidth, int guiHeight, int guiScale) {
        // Honor the user's GUI scale. Only shrink when the panel would exceed the window margins.
        float scale = (float) Math.min(1, Math.min(guiWidth * .82 / 540, guiHeight * .82 / 260));
        return new EditorViewport(scale, (int) Math.ceil(guiWidth / scale), (int) Math.ceil(guiHeight / scale));
    }
    public double input(double coordinate) { return coordinate / scale; }
}
