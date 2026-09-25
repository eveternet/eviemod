package dev.eviemod.features.dungeons;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartyFinderAlertTest {
    private static final String MESSAGE =
        "Party Finder > Your dungeon group is full! Click here to warp to the dungeon!";

    @Test void displaysDefaultAndCustomTextOncePerMessage() {
        var output = new ArrayList<String>();
        PartyFinderAlert.receive(MESSAGE, false, true, PartyFinderAlert.DEFAULT_SUBTITLE, output::add);
        PartyFinderAlert.receive(MESSAGE, false, true, "Ready to go!", output::add);
        assertEquals(List.of("Party is full!", "Ready to go!"), output);
    }

    @Test void acceptsLegacyFormattingAndStyledComponentSiblingsWithoutMutation() {
        var component = Component.literal("Party Finder > ").withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Your dungeon group is full! ").withStyle(ChatFormatting.YELLOW))
            .append(Component.literal("Click here to warp to the dungeon!").withStyle(ChatFormatting.GREEN));
        var original = component.copy();
        var output = new ArrayList<String>();
        PartyFinderAlert.receive(component.getString(), false, true, "Full!", output::add);
        PartyFinderAlert.receive("  §6Party Finder §r> §aYour dungeon group is full! "
            + "§LClick here to warp to the dungeon!§r  ", false, true, "Full!", output::add);
        assertEquals(List.of("Full!", "Full!"), output);
        assertEquals(original, component);
    }

    @Test void ignoresDisabledOverlayAndBlankSubtitles() {
        var output = new ArrayList<String>();
        PartyFinderAlert.receive(MESSAGE, false, false, "Full!", output::add);
        PartyFinderAlert.receive(MESSAGE, true, true, "Full!", output::add);
        PartyFinderAlert.receive(MESSAGE, false, true, "", output::add);
        PartyFinderAlert.receive(MESSAGE, false, true, "   ", output::add);
        assertTrue(output.isEmpty());
    }

    @Test void ignoresQuotesNearMatchesAndMissingInput() {
        var output = new ArrayList<String>();
        for (String line : new String[]{null, "", "Party > Player: " + MESSAGE,
            "[MVP+] Player: " + MESSAGE, MESSAGE + " extra", MESSAGE.replace("dungeon", "kuudra"),
            "Party Finder > Your dungeon group is full!", "Your dungeon group is full!"}) {
            PartyFinderAlert.receive(line, false, true, "Full!", output::add);
        }
        assertTrue(output.isEmpty());
    }
}
