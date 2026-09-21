package dev.eviemod.features.dungeons;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnnounceCritTest {
    private static String summary(String enemies, String damage) {
        return "Your Explosive Shot hit " + enemies + " for " + damage + " damage.";
    }
    @Test void parsesSingularPluralAndRoundsWholeNumbers() {
        assertEquals("Explosive Shot did 400,000 damage to enemies.",
            AnnounceCrit.render(summary("3 enemies", "1,200,000"), AnnounceCrit.DEFAULT_TEMPLATE));
        assertEquals("1,201", AnnounceCrit.render(summary("1 enemy", "1,200.5"), "{damage}"));
        assertEquals("333", AnnounceCrit.render(summary("3 enemies", "1,000"), "{damage}"));
        assertEquals("0", AnnounceCrit.render(summary("1 enemy", "0"), "{damage}"));
    }
    @Test void substitutesLiterallyWithoutInterpretingOtherText() {
        assertEquals("$400,000 / 400,000 {other}", AnnounceCrit.render(summary("3 enemies", "1,200,000"), "$\u007bdamage} / {damage} {other}"));
        assertEquals("Custom text", AnnounceCrit.render(summary("1 enemy", "10"), "Custom text"));
    }
    @Test void rejectsMalformedOrUnrelatedMessages() {
        for (String damage : new String[]{"1,20", "1..2", ",", "NaN", "-1", "1,000,", "1.2.3"})
            assertNull(AnnounceCrit.render(summary("1 enemy", damage), "{damage}"));
        assertNull(AnnounceCrit.render(summary("0 enemies", "100"), "{damage}"));
        assertNull(AnnounceCrit.render("Party > player: " + summary("1 enemy", "100"), "{damage}"));
        assertNull(AnnounceCrit.render(summary("1 enemy", "100") + " extra", "{damage}"));
    }
    @Test void selectsExactlyOneOutputAndIgnoresOverlayOrDisabled() {
        var local = new ArrayList<String>(); var commands = new ArrayList<String>();
        String input = summary("3 enemies", "1,200,000");
        AnnounceCrit.receive(input, false, false, "{damage}", true, local::add, commands::add);
        AnnounceCrit.receive(input, true, true, "{damage}", true, local::add, commands::add);
        assertTrue(local.isEmpty()); assertTrue(commands.isEmpty());
        AnnounceCrit.receive(input, false, true, "Crit {damage}", false, local::add, commands::add);
        assertEquals(java.util.List.of("Crit 400,000"), local); assertTrue(commands.isEmpty());
        local.clear();
        AnnounceCrit.receive(input, false, true, "Crit {damage}", true, local::add, commands::add);
        assertTrue(local.isEmpty()); assertEquals(java.util.List.of("pc Crit 400,000"), commands);
    }
}
