package dev.eviemod.paintbrush;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Settings only. Defaults intentionally match Kabeewie Unified 1.2.0. */
public final class ImportedFeatures {
    public Skyblock skyblock = new Skyblock();
    public Garden garden = new Garden();
    public SoulWhip soulWhip = new SoulWhip();
    public static final class SoulWhip { public boolean enabled = true; }
    public static final class Skyblock {
        public boolean noBarrierEffects = true;
        public boolean maxTenHearts = true;
        public boolean commandHotkeysEnabled = true;
        public List<Hotkey> commandHotkeys = new ArrayList<>();
        public Map<String, Channels> partyCommands = new LinkedHashMap<>();
        public Channels channels(String key) { return partyCommands.computeIfAbsent(key, ignored -> new Channels()); }
    }
    public static final class Channels {
        public boolean party = true;
        public boolean guild = false;
        public boolean coop = false;
    }
    public static final class Hotkey {
        public int key = -1;
        public String command = "";
    }
    public static final class Garden {
        public boolean mouseLock = false;
        // Serialization-only legacy data: retain saved values and the existing migration format.
        // No runtime setting, UI binding, getter or pest workflow remains.
        @com.google.gson.annotations.SerializedName("forceFinnegan")
        private boolean retiredPestValue = false;
        public int teleportPlot = 1;
        public Map<String, String> keys = new LinkedHashMap<>();
    }
    static final class Migrations {
        boolean skyblock, garden, soulWhip;
    }
}
