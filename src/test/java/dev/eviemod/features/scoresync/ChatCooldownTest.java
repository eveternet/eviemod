package dev.eviemod.features.scoresync;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatCooldownTest {
    @Test void narrowDurationParsing() {
        assertEquals(3000L, ChatCooldown.delay("You can only chat once every 3 seconds! Ranked users can bypass this restriction!"));
        assertEquals(500L, ChatCooldown.delay("You can only chat once every 0.5 seconds!"));
        assertNull(ChatCooldown.delay("You must wait for an ability cooldown!"));
        assertNull(ChatCooldown.delay("You can only chat once every -1 seconds!"));
        assertNull(ChatCooldown.delay("You can only chat once every 999999999999999999999999 seconds!"));
    }
}
