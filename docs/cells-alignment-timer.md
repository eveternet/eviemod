# Cells Alignment timer

The default-off **Dungeons → Cells Alignment Timer** setting shows a main-title
countdown only for the local Tank in a Hypixel Catacombs dungeon. A formatted
system-chat message must consist of a Minecraft username followed by the exact
` cast Cells Alignment on you!` suffix. Player-chat prefixes, arbitrary mentions,
action bars, and malformed messages do not trigger it. A repeated cast restarts it.

| Estimated server ticks since receipt | Main title | Color |
| --- | --- | --- |
| 0–19 | Cells Aligned Casted! | Green `#55ff55` |
| 20–39 | 4 | Green `#55ff55` |
| 40–59 | 3 | Green `#55ff55` |
| 60–79 | 2 | Orange/gold `#ffaa00` |
| 80–99 | 1 | Red `#ff5555` |
| 100–159 | Esoward now | Red `#ff5555` |
| 160 onward | Removed immediately | No fade |

## Timing choice and limits

The user explicitly chose TPS-based estimation over conservative time-packet
counting. The adapter observes the existing `handleSetTime` packet hook after
vanilla's client-thread handling, using `packet.gameTime()` rather than the client
world's locally incremented time. Positive server world-age deltas divided by
monotonic packet-receipt intervals determine the latest estimated TPS, capped at
20. Duplicate updates are ignored; a backwards server clock cancels the timer.

Between samples, monotonic elapsed time advances a fractional tick timeline at
the last measured TPS. It starts immediately at chat receipt, without waiting for
the next sample. Each sample changes the future rate without jumping the existing
countdown. Before two useful samples, the rate is nominally 20 TPS. At a measured
10 TPS the countdown lasts 16 wall-clock seconds. Recasting retains the measured
TPS but resets elapsed ticks. World/connection changes clear both timer and rate
history; leaving the dungeon, disabling the setting, and losing a known Tank class
cancel the active timer. Disconnect explicitly clears all state.

This is an estimate, not exact cast-relative server ticks. Cached Minecraft 26.2
sources show periodic time sync every 20 server ticks, but system-chat packets do
not carry a tick timestamp. Network jitter/batching can skew a sample. Sudden lag,
a full server stall, missing updates, or server/proxy-specific time handling can
cause drift: the countdown keeps extrapolating until the next useful sample. No
wall-clock-only or client-tick counter is presented as an exact server clock.
The existing party-command TPS display is unchanged.

## Detection and rendering boundary

The supplied screenshot was visually inspected: the party header is `Party (1)`;
the player row is `[500] eveternet (Berserk XLI)`, with `Dungeon: Catacombs` in tab
and `The Catacombs (M7)` in the SKYBLOCK sidebar. Detection requires the Hypixel
connection, SKYBLOCK sidebar, a supported Catacombs floor (E, F1–7, M1–7), that
exact dungeon tab marker, and a matching local username with `(Tank <level>)`.
The player need not occupy the first tab entry. Unknown/conflicting local classes
fail closed. Tank substitution is a fixture assumption based on the shown class
format; no live Hypixel validation was performed.

Vanilla titles and subtitles share one timer. Small version-specific mixins in
`Gui` (26.1.2) and `Hud` (26.2) draw the countdown at vanilla's main-title scale and
position, then suppress only the original main-title draw while active. They do
not write title/subtitle text or timing fields. The Party Finder subtitle keeps
its own normal duration and fade, even if triggered during this countdown.
Existing server titles continue aging normally and become visible again if still
active after this feature ends. The custom title always has full alpha and is
absent from the first extracted frame at or after 160 estimated ticks.

Validation is headless: deterministic logic/settings tests, compiled-target
bytecode checks, and direct wrapper tests. It does not establish live modpack
compatibility or replace an in-game visual check. No game launch is needed for
these tests.
