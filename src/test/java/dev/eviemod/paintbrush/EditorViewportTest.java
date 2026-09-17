package dev.eviemod.paintbrush;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EditorViewportTest {
    @Test void fitsDesktopLaptopAndSmallWindowsAtEveryGuiScale() {
        for (int[] resolution : new int[][] {{3840,2160}, {1920,1080}, {1280,720}, {854,480}, {640,360}}) {
            for (int guiScale : new int[] {1,2,3,4,6,8}) {
                int width = resolution[0] / guiScale, height = resolution[1] / guiScale;
                var v = EditorViewport.fit(width, height, guiScale);
                assertTrue(540 * v.scale() <= width * .821);
                assertTrue(260 * v.scale() <= height * .821);
                assertTrue(v.width() >= 540 && v.height() >= 260);
                assertTrue(v.scale() <= 1);
                assertEquals(123.5, v.input(123.5 * v.scale()), .0001);
            }
        }
    }
    @Test void highGuiScaleNoLongerMakesEditorFillFourKWindow() {
        var v = EditorViewport.fit(480, 270, 8);
        assertEquals(480 * .82 / 540, v.scale(), .00001);
        assertTrue(v.scale() > .7);
    }
    @Test void honorsNativeGuiScaleWhenEditorFits() {
        assertEquals(1, EditorViewport.fit(960, 540, 4).scale());
        assertEquals(1, EditorViewport.fit(1280, 720, 3).scale());
    }
}
