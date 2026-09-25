package dev.eviemod.paintbrush;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settings only. Features remain off until explicitly enabled. */
public final class ImportedFeatures {
    public Skyblock skyblock = new Skyblock();
    public Dungeons dungeons = new Dungeons();
    public static final class Dungeons {
        public boolean partyFinderAlert = false;
        public String partyFinderSubtitle = dev.eviemod.features.dungeons.PartyFinderAlert.DEFAULT_SUBTITLE;
        public boolean announceCrit = false;
        public boolean announceCritPartyChat = false;
        public String announceCritTemplate = dev.eviemod.features.dungeons.AnnounceCrit.DEFAULT_TEMPLATE;
    }
    public Garden garden = new Garden();
    public SoulWhip soulWhip = new SoulWhip();
    public static final class SoulWhip { public boolean enabled = false; }
    public static final class Skyblock {
        public boolean noammScoreSync = false;
        public boolean noBarrierEffects = false;
        public boolean maxTenHearts = false;
        public boolean commandHotkeysEnabled = false;
        public List<Hotkey> commandHotkeys = new ArrayList<>();
        public Map<String, Channels> partyCommands = new LinkedHashMap<>();
        public Channels channels(String key) { return partyCommands.computeIfAbsent(key, ignored -> new Channels()); }
    }
    public static final class Channels {
        public boolean party = false;
        public boolean guild = false;
        public boolean coop = false;
    }
    public static final class Hotkey {
        public int key = -1;
        public String command = "";
    }
    public static final class Garden {
        public boolean mouseLock = false;
        public int teleportPlot = 1;
        public Map<String, String> keys = new LinkedHashMap<>();
    }
    static final class Migrations {
        boolean skyblock, garden, soulWhip;
    }
}
