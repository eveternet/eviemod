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
connection, the HM API SkyBlock location or scoreboard context, a six-row vanilla container, a recognized
experiment title and its explicit feature toggle. Stakes, reward and other
screens are excluded. Unsupported or missing observations pass input through.

Guidance is extracted once at the tail of `AbstractContainerScreen.extractRenderState`,
on its own GUI stratum. It does not depend on vanilla
`extractSlot` reaching its tail: other mods, including SkyHanni's Better Containers,
can cancel individual slot extraction. Remembered-item tooltips are installed in
the same container hook. On both supported versions, `extractContents` has restored
the geometry pose before this hook, and the outer
`Screen.extractRenderStateWithTooltipAndSubtitles` flushes deferred tooltips after
the container returns. Tooltips therefore render above guidance in the same
frame. Fabric's `ScreenEvents.afterExtract` runs after that flush and cannot
serve as this tooltip boundary. Input retains the narrow `slotClicked` hook.
Manual pickup clicks and button-2 `CLONE` clicks are recognized; SkyHanni's
experiment helper rewrites correct manual clicks to the latter through
`SlotClickEvent.makePickblock()`. Unsupported modes still pass through and
discard sequence guidance. Eviemod never rewrites an input mode itself.

The maintained [Skyblocker experiment sources](https://github.com/SkyblockerMod/Skyblocker/tree/main/src/main/java/de/hysky/skyblocker/skyblock/experiment)
were inspected on 2026-10-13. They establish the slot-49 instruction text,
Chronomatron glint pulses and terracotta/glass color equivalence, numbers in
slots 9–44, and the cyan Superpairs second-choice placeholder. The independent
[NEU implementation](https://github.com/NotEnoughUpdates/NotEnoughUpdates/blob/master/src/main/java/io/github/moulberry/notenoughupdates/miscfeatures/EnchantingSolvers.java)
confirms glowstone/clock phase items and lore identifying powerups. SkyHanni's
[experiment API](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/api/ExperimentationTableApi.kt)
includes the Beginner Superpairs tier. These are protocol evidence, not live
validation of this implementation; no third-party feature code is bundled.
SkyHanni's current
[addon helper](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/features/inventory/experimentationtable/ExperimentsAddonsHelper.kt)
also supplies formatted slot-4 `Round: N` examples, while its
[container mixin](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/mixins/transformers/MixinAbstractContainerScreen.java)
and [slot-click event](https://github.com/hannibal002/SkyHanni/blob/beta/src/main/java/at/hannibal2/skyhanni/events/GuiContainerEvent.kt)
establish the rendering-cancellation and manual-pickblock coexistence boundaries.

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

Only copied server packet payloads for the current container supply observations.
The adapter maintains its own board instead of scanning the live menu after a
packet: unchanged live slots can still hold unacknowledged client predictions.
Client click prediction, GUI resize and unrelated inventory packets are not reveal evidence.
A screen close/reopen, connection/world change, disabled helper or unsupported
phase discards the session; a changed known Superpairs card invalidates its old
board. Sequence rounds discard the preceding sequence. A pending Superpairs click
cannot reuse an unchanged second-choice snapshot. Unknown input modes pass
through and invalidate guidance. Server data and live item components are never
modified by drawing remembered items.

## Live failure investigation

The user's 2026-10-13 launcher log confirms eviemod `1.4.0.0` on Minecraft 26.2,
Skyblocker `6.10.4+26.2`, SkyHanni `9.1.0` on 26.2, and completed Chronomatron,
Ultrasequencer and Superpairs sessions. It has no Eviemod exception or experiment
packet/slot capture. That log does not prove which activation or protocol gate
suppressed the original guidance. The screen callback and manual-pickblock fixes
address verified mod integration boundaries; their success in the user's game
still requires observation.
The installed JAR matches the delivered JAR, all four Eviemod experiment choices
are enabled, and both Skyblocker and SkyHanni experiment helpers are disabled in
the identified instance. The manual-pickblock boundary is therefore not the
observed failure's cause. Activation now accepts the same existing HM API
SkyBlock location used by Quick Navigation, with the current Hypixel connection
still required, so a missing scoreboard cannot suppress a recognized table.

`ExperimentClient.diagnosticReport()` supports an explicitly requested bounded
capture. It reports feature choices, API and scoreboard context, the current activation gate, screen type,
title and container ID, accepted experiment packets and overlay counts, the
registry IDs/names of only instruction slots 4 and 49, and sequence progress.
The last closed experiment is retained for the same connection so opening chat
to issue a diagnostic command does not erase the useful evidence. It includes
no player inventory, item components, chat, player identity or server address.
Up to four recent six-row Hypixel container candidates retain their actual
class/title, API/scoreboard gates and only slots 4/49, even if title parsing or
activation failed. These are explicitly marked live-menu snapshots and are
never fed into the solver's server-observation state. Matching packet counts
remain separate from accepted solver packets. The history uses a weak screen
reference and is cleared on world/connection change or disconnect.
If overlays remain absent, capture once after a round's replay phase; those
counts distinguish missing server observations from incomplete parsing or a
rendering problem without requiring repeated blind attempts.

## Validation

Both Minecraft 26.1.2 and 26.2 compile the same implementation and regression
suite. Tests exercise repeated and missed pulses, duplicate snapshots, wrong
clicks with protection on/off, partial numbered packets, missing/duplicate
numbers, rounds, reopened sessions, board changes, different enchantment
identities, powerups, unknown exploration, packet-copy isolation from live menu
prediction, malformed protocol data, settings
persistence and default-off resets. The existing version launch fixture loads
both new mixin owners and renders the shared configuration categories in CI.
`ExperimentRenderBoundaryTest` reads the actual mapped Minecraft bytecode on
each target to check slot/pose completion before the container returns, its
return before deferred tooltip flushing, and the tooltip's later GUI stratum.
It also verifies the compiled overlay injection stays at that container boundary.
This guards the version-sensitive ordering contract without starting a game;
it does not replace live visual or modpack validation.

The root `./gradlew build` validates both main targets and both existing Metal
addons. Artifact checks verify the matching main-mod version and independent
addon distributions. Local graphical fixtures are deferred to CI to avoid
interfering with the simultaneous Wishing Compass task or an active game client.
Live Hypixel behavior, timing, the round label, server acknowledgment and full
modpack coexistence remain unverified until separately tested in game.
