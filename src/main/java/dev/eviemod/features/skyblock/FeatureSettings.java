package dev.eviemod.features.skyblock;

import dev.eviemod.paintbrush.EviemodSettings;
import dev.eviemod.paintbrush.ImportedFeatures;

/** Read-only adapter: feature logic never owns or saves configuration. */
public final class FeatureSettings {
    private static ImportedFeatures.Skyblock settings() { return EviemodSettings.features().skyblock; }
    public static boolean isSoulWhipFixEnabled() { return EviemodSettings.features().soulWhip.enabled; }
    public static boolean isNoBarrierEffectsEnabled() { return settings().noBarrierEffects; }
    public static boolean isMaxTenHeartsEnabled() { return settings().maxTenHearts; }
    public static boolean isCommandHotkeysEnabled() { return settings().commandHotkeysEnabled; }
    public static int getCommandHotkeyCount() { return settings().commandHotkeys.size(); }
    public static int getCommandHotkeyKey(int slot) { return settings().commandHotkeys.get(slot).key; }
    public static String getCommandHotkeyCommand(int slot) { return settings().commandHotkeys.get(slot).command; }
    public static boolean isPartyCommandEnabled(String key, PartyCommandController.CommandChannel channel) {
        var channels = settings().partyCommands.get(key);
        if (channels == null) return channel == PartyCommandController.CommandChannel.PARTY;
        return switch (channel) {
            case PARTY -> channels.party;
            case GUILD -> channels.guild;
            case COOP -> channels.coop;
        };
    }
}
