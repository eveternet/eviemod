package dev.eviemod.features.dungeons;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CellsAlignmentTimerTest {
    static final String CAST = "Some_Player cast Cells Alignment on you!";

    @Test void exactSystemMessageAcceptsFormattingButNotChatMentions() {
        for (String text : new String[]{CAST, "  §bSome_Player §rcast §aCells Alignment on you!§r  ",
            Component.literal("Some_Player").withColor(0x55ffff)
                .append(Component.literal(" cast Cells Alignment on you!").withColor(0x55ff55)).getString()}) {
            assertTrue(CellsAlignmentTimer.matches(text), text);
        }
        for (String text : new String[]{"", "cast Cells Alignment on you!", "[MVP+] " + CAST,
            "Player: " + CAST, "Party > " + CAST, CAST + " extra", "x\n" + CAST,
            "Some_Player cast Cells Alignment on someone!", "Some_Player cast Cells Alignment on you",
            "Some_Player cast cells alignment on you!", "abcdefghijklmnopq cast Cells Alignment on you!"}) {
            assertFalse(CellsAlignmentTimer.matches(text), text);
        }
        assertFalse(CellsAlignmentTimer.matches(null));
    }

    @Test void everyServerTickHasTheRequestedTextAndColorThenVanishes() {
        var timer = new CellsAlignmentTimer();
        assertTrue(timer.receive(CAST, false, true));
        String[] texts = {"Cells Aligned Casted!", "4", "3", "2", "1", "Esoward now"};
        int[] colors = {0x55ff55, 0x55ff55, 0x55ff55, 0xffaa00, 0xff5555, 0xff5555};
        for (int tick = 0; tick < 160; tick++) {
            int stage = Math.min(tick / 20, 5);
            assertEquals(new CellsAlignmentTimer.Frame(texts[stage], colors[stage]), timer.frame(), "tick " + tick);
            timer.advance(1);
        }
        assertNull(timer.frame());
        timer.advance(20);
        assertNull(timer.frame());
    }

    @Test void renderingAndClientActivityCannotAdvanceTheServerTimeline() {
        var timer = new CellsAlignmentTimer();
        timer.receive(CAST, false, true);
        timer.advance(19);
        for (int clientTick = 0; clientTick < 10000; clientTick++) {
            assertEquals("Cells Aligned Casted!", timer.frame().text());
        }
        timer.advance(1);
        assertEquals("4", timer.frame().text());
        timer.advance(80);
        assertEquals("Esoward now", timer.frame().text());
        for (int clientTick = 0; clientTick < 10000; clientTick++) {
            assertEquals("Esoward now", timer.frame().text());
        }
        timer.advance(59);
        assertNotNull(timer.frame());
        timer.advance(1);
        assertNull(timer.frame());
    }

    @Test void gatingRetriggerResetAndUnrelatedMessages() {
        var timer = new CellsAlignmentTimer();
        assertFalse(timer.receive(CAST, false, false));
        assertFalse(timer.receive(CAST, true, true));
        assertNull(timer.frame());
        timer.receive(CAST, false, true);
        timer.advance(120);
        assertFalse(timer.receive("Party > " + CAST, false, true));
        assertEquals("Esoward now", timer.frame().text());
        assertTrue(timer.receive(CAST, false, true));
        assertEquals("Cells Aligned Casted!", timer.frame().text());
        timer.advance(159);
        assertNotNull(timer.frame());
        timer.reset();
        assertNull(timer.frame());
        timer.receive(CAST, false, true);
        timer.receive("unrelated", false, false);
        assertNull(timer.frame());
    }

    @Test void largeOrInvalidDeltasCannotOverflowOrRunBackwards() {
        var timer = new CellsAlignmentTimer();
        timer.receive(CAST, false, true);
        timer.advance(-1);
        timer.advance(0);
        assertEquals("Cells Aligned Casted!", timer.frame().text());
        timer.advance(Long.MAX_VALUE);
        assertNull(timer.frame());
    }
}
