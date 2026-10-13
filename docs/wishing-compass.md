# Wishing Compass solver

Enable **Mining → Wishing Compass Solver** in `/eviemod`. The default is off,
including existing configurations and resets. Manually use a compass outside the
Crystal Nucleus, wait five seconds, move at least eight blocks sideways within the same region,
and use another. A usable pair shows a world marker, distance, approximate
coordinates and an uncertainty radius. The destination is always an estimate;
the radius is a geometric error allowance, not a statistical confidence interval.

Only the local player's vanilla item/block use callbacks arm observation. The
stable SkyBlock custom-data ID must be `WISHING_COMPASS`, and the connection must
be Hypixel with the HM API mode `crystal_hollows`. The existing location-event
subscription is reused. There is no automatic item use, location exchange,
third-party service, websocket, chat-coordinate ingestion or saved waypoint file.

The particle hook observes `ClientPacketListener.handleParticleEvent` after
vanilla handling, on its game-thread path. Happy-villager packet origins form a
bounded candidate sequences starting near the use position. Three matching points
in a repeat pass are required before a first pass can qualify. The full five-second
window closes before accepting an observation or rendering any waypoint. Multiple
coherent candidates cause rejection, including a late candidate whose repeat is
incomplete. Completed candidates do not block a later candidate at the same start.
Repeated passes along the same axis within the fitted error allowance count as one trail. Packet or
candidate overflow also causes rejection rather than silently losing evidence. Bursts with
randomized offsets, missing repeats, short trails, curved/backtracking samples,
parallel rays, backward intersections, excessive skew, implausible Hollows
coordinates and wide uncertainty produce guidance without a waypoint. A five
second window limits capture; observations/results expire after five minutes.

The two fitted rays use a covariance axis and closest forward approach in 3D.
Uncertainty includes sample deviation, a nonzero angular floor, crossing-angle
amplification and the rays' separation. This cannot prove that both compass
uses had the same server-selected target. Region, Jungle Key presence and
visible crystal/King's Scent changes reset observations. The parser recognizes the
separate `Crystals:` heading and all five name/status rows, including
`Amethyst: ✖ Not Found`, `Amethyst: ✔ Found` and `Amethyst: ✔ Placed`.
Missing, partial, conflicting or unknown crystal data cannot qualify as verified
stable prerequisites. Enable the Crystals tab widget before solving; Goblin uses
also need a known scent state. A narrow, game-thread tab-footer packet snapshot
joins the list entries for scent presence/removal; it is bound to the world and
connection and discarded on lobby changes. Other unseen
target changes require **Clear compass observations**. Region checks protect
unsolved observation pairs; a solved waypoint survives crossing region boundaries
while navigating, including through the nucleus. Starting a solve inside the
inclusive nucleus guard `(462,63,461)–(564,181,565)` is unsupported; above Y181 the
quadrant model applies. Scent countdown changes do not invalidate observations. No structure name or
temple entrance offset is inferred from the estimated coordinates.

World/connection identity changes, lobby/server/mode changes, leaving the
Hollows, disabling the setting and manual clearing discard pending observations
and estimates. Repeated location updates for the same lobby preserve them.
Native per-tick Minecraft gizmos provide the marker and uncertainty ring; Fabric's
HUD API provides capture guidance. Both are suppressed when the HUD is hidden.

Protocol research: [NEU's compass implementation](https://github.com/NotEnoughUpdates/NotEnoughUpdates/blob/master/src/main/java/io/github/moulberry/notenoughupdates/miscfeatures/CrystalWishingCompassSolver.java)
documents happy-villager particles, repeated trails and a five-second capture
window. Its source is LGPL-3.0-or-later; no implementation was copied or bundled.
The geometric solver and tests here were written independently. The particle
protocol carries no originating-player identity, so nearby simultaneous uses
cannot be attributed with certainty; the own-use window, proximity, sequence
and repeat checks reduce accidental matches, rather than establishing ownership.
The [pinned Skyblocker reference](https://github.com/SkyblockerMod/Skyblocker/blob/a3f25d798b5ea8d8a91a690232bbd4274b4a7a3a/src/main/java/de/hysky/skyblocker/skyblock/dwarven/WishingCompassSolver.java)
confirms the separate crystal rows and nucleus exclusion. NEU also checks the
tab footer for scent. These are reference-source assumptions, not a current
live Hypixel capture; no implementation from either reference is copied or bundled.

Unit fixtures cover observation lifecycle, geometric rejection, triangulation,
uncertainty and default-off persistence. The shared code is compiled and tested
for Minecraft 26.1.2 and 26.2. CI's offline client fixtures load the packet mixin
and render the shared settings. Offline results do not validate current live
Hypixel particle timing, item uses, target selection or crowded-lobby attribution.
Regressions include the independent review's crystal-format collision and
competing-trail replay, acquisition/placement, missing data, nucleus boundaries,
footer-only scent changes, countdown stability, world-bound footer snapshots,
sequential/interleaved/late competitors and same-trail repeats. Live Hypixel
testing must be reported separately.
