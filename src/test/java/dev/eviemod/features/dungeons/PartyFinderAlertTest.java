package dev.eviemod.features.dungeons;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Style;
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
        var component = Component.literal("Party Finder > ").withStyle(Style.EMPTY.withColor(0xffaa00))
            .append(Component.literal("Your dungeon group is full! ").withStyle(Style.EMPTY.withColor(0xffff55)))
            .append(Component.literal("Click here to warp to the dungeon!").withStyle(Style.EMPTY.withColor(0x55ff55)));
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

    @Test void soundAndSubtitleCanRunIndependentlyAndTogether() {
        var subtitles = new ArrayList<String>();
        var sounds = new ArrayList<PartyFinderAlert.Sound>();
        PartyFinderAlert.receive(MESSAGE, false, false, "Full!", true,
            "  minecraft:entity.experience_orb.pickup  ", 0.35F, 1.5F, subtitles::add, sounds::add);
        assertTrue(subtitles.isEmpty());
        assertEquals(List.of(new PartyFinderAlert.Sound("minecraft:entity.experience_orb.pickup", 0.35F, 1.5F)), sounds);
        PartyFinderAlert.receive(MESSAGE, false, true, " ", true,
            PartyFinderAlert.DEFAULT_SOUND, 1.0F, 1.0F, subtitles::add, sounds::add);
        assertTrue(subtitles.isEmpty());
        assertEquals(2, sounds.size());
        PartyFinderAlert.receive(MESSAGE, false, true, "Full!", true,
            PartyFinderAlert.DEFAULT_SOUND, 0.5F, 2.0F, subtitles::add, sounds::add);
        assertEquals(List.of("Full!"), subtitles);
        assertEquals(3, sounds.size());
        PartyFinderAlert.receive(MESSAGE, false, true, "Full!", false,
            PartyFinderAlert.DEFAULT_SOUND, 1.0F, 1.0F, subtitles::add, sounds::add);
        assertEquals(List.of("Full!", "Full!"), subtitles);
        assertEquals(3, sounds.size());
    }

    @Test void soundUsesTheSameFormattingAndFullLineGuards() {
        var sounds = new ArrayList<PartyFinderAlert.Sound>();
        for (String line : new String[]{null, "", "Party > Player: " + MESSAGE,
            MESSAGE + " extra", MESSAGE.replace("dungeon", "kuudra")}) {
            PartyFinderAlert.receive(line, false, false, "", true,
                PartyFinderAlert.DEFAULT_SOUND, 1.0F, 1.0F, text -> fail(), sounds::add);
        }
        PartyFinderAlert.receive(MESSAGE, true, false, "", true,
            PartyFinderAlert.DEFAULT_SOUND, 1.0F, 1.0F, text -> fail(), sounds::add);
        PartyFinderAlert.receive(MESSAGE, false, false, "", false,
            PartyFinderAlert.DEFAULT_SOUND, 1.0F, 1.0F, text -> fail(), sounds::add);
        assertTrue(sounds.isEmpty());
        PartyFinderAlert.receive("  §6" + MESSAGE + "§r  ", false, false, "", true,
            PartyFinderAlert.DEFAULT_SOUND, 1.0F, 1.0F, text -> fail(), sounds::add);
        assertEquals(List.of(new PartyFinderAlert.Sound(PartyFinderAlert.DEFAULT_SOUND, 1.0F, 1.0F)), sounds);
    }

    @Test void badSoundSettingsDoNotSuppressSubtitle() {
        var subtitles = new ArrayList<String>();
        for (String id : new String[]{null, "", " "}) {
            PartyFinderAlert.receive(MESSAGE, false, true, "Full!", true,
                id, 1.0F, 1.0F, subtitles::add, sound -> fail());
        }
        for (float[] levels : new float[][]{{-1, 1}, {2, 1}, {Float.NaN, 1},
            {1, 0.4F}, {1, 2.1F}, {1, Float.POSITIVE_INFINITY}}) {
            PartyFinderAlert.receive(MESSAGE, false, true, "Full!", true,
                PartyFinderAlert.DEFAULT_SOUND, levels[0], levels[1], subtitles::add, sound -> fail());
        }
        assertEquals(9, subtitles.size());
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
