# Experimentation Table helpers

`/eviemod` → **Experiments** exposes separate Chronomatron, Ultrasequencer and
Superpairs toggles plus **Protect incorrect clicks**. All four default off on
fresh installs, existing settings without these fields and configuration reset.
The shared settings store validates their types and preserves malformed files.
No additional screen, input binding or dependency is introduced.

Chronomatron records each displayed glint pulse, including consecutive repeats,
and outlines the next note in green during manual replay. Ultrasequencer retains
the numbered reveal, displays each remembered number and outlines the next
position in green. Superpairs draws copies of remembered items over hidden cards,
including their counts and hover tooltips. Yellow outlines identify known pairs;
green identifies a known match for the currently revealed first card.

Protection applies only to an enabled helper. A complete, observed replay permits
rejecting a wrong Chronomatron note or Ultrasequencer position. Superpairs rejects
only a previously revealed, known nonmatching reward when the server shows one
unambiguous first reward and the second-choice prompt. Unknown cards and powerups
remain available to explore. A blocked click does not advance the helper. An
allowed replay mistake invalidates guidance. There is no automatic clicking,
packet generation, replay, early-click blocking or click-rate change.

## Protocol boundary and safety

`ExperimentMenu` centralizes Hypixel GUI heuristics; `ExperimentSession` contains
Minecraft-independent state; `ExperimentClient` adapts inventory packets, screen
lifecycle, drawing and manual input. Activation requires the actual Hypixel
connection, the SkyBlock context, a six-row vanilla container, a recognized
experiment title and its explicit feature toggle. Stakes, reward and other
screens are excluded. Unsupported or missing observations pass input through.

The maintained [Skyblocker experiment sources](https://github.com/SkyblockerMod/Skyblocker/tree/main/src/main/java/de/hysky/skyblocker/skyblock/experiment)
were inspected on 2026-10-13. They establish the slot-49 instruction text,
Chronomatron glint pulses and terracotta/glass color equivalence, numbers in
slots 9–44, and the cyan Superpairs second-choice placeholder. The independent
[NEU implementation](https://github.com/NotEnoughUpdates/NotEnoughUpdates/blob/master/src/main/java/io/github/moulberry/notenoughupdates/miscfeatures/EnchantingSolvers.java)
confirms glowstone/clock phase items and lore identifying powerups. SkyHanni's
[experiment API](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/api/ExperimentationTableApi.kt)
includes the Beginner Superpairs tier. These are protocol evidence, not live
validation of this implementation; no third-party feature code is bundled.

The adapter requires the English `Remember the pattern!` glowstone or `Timer: N`
clock instruction. A replay is trusted only when slot 4 has a valid `Round: N`
label and all N reveals were observed. This round-label assumption needs a live
capture; a changed label suppresses sequence guidance and protection safely.
Ultrasequencer number labels must agree with stack counts. Duplicate numbers,
out-of-range numbers, ambiguous pulses and incomplete sequences suppress replay
guidance. Stable Minecraft registry identifiers avoid the grouped item constants
that exist only in 26.2. Superpairs uses full copied ItemStack equality, including
counts and components, so different enchantments or metadata cannot be guessed
as matching. Ambiguous identities may miss a match rather than invent one.

Only server-applied packets for the current container supply observations.
Client click prediction and unrelated inventory packets are not reveal evidence.
A screen close/reopen, connection/world change, disabled helper or unsupported
phase discards the session; a changed known Superpairs card invalidates its old
board. Sequence rounds discard the preceding sequence. A pending Superpairs click
cannot reuse an unchanged second-choice snapshot. Unknown input modes pass
through and invalidate guidance. Server data and live item components are never
modified by drawing remembered items.

## Validation

Both Minecraft 26.1.2 and 26.2 compile the same implementation and regression
suite. Tests exercise repeated and missed pulses, duplicate snapshots, wrong
clicks with protection on/off, partial numbered packets, missing/duplicate
numbers, rounds, reopened sessions, board changes, different enchantment
identities, powerups, unknown exploration, malformed protocol data, settings
persistence and default-off resets. The existing version launch fixture loads
both new mixin owners and renders the shared configuration categories in CI.

The root `./gradlew build` validates both main targets and both existing Metal
addons. Artifact checks verify the matching main-mod version and independent
addon distributions. Local graphical fixtures are deferred to CI to avoid
interfering with the simultaneous Wishing Compass task or an active game client.
Live Hypixel behavior, timing, the round label, server acknowledgment and full
modpack coexistence remain unverified until separately tested in game.
