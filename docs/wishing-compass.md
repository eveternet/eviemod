# Wishing Compass solver

Enable **Mining → Wishing Compass Solver** in `/eviemod`. The default is off,
including existing configurations and resets. Manually use a compass outside the
Crystal Nucleus, wait for **Trail recorded**, move at least eight blocks sideways within the same region,
and use another. A usable pair shows a world marker, distance, approximate
coordinates and an uncertainty radius. The destination is always an estimate;
the radius is a geometric error allowance, not a statistical confidence interval.

Only vanilla item/block use packets actually sent by the local connection arm observation. The
stable SkyBlock custom-data ID must be `WISHING_COMPASS`, and the connection must
be Hypixel with the HM API mode `crystal_hollows`. The existing location-event
subscription is reused. There is no automatic item use, location exchange,
third-party service, websocket, chat-coordinate ingestion or saved waypoint file.
`MultiPlayerGameMode.startPrediction` snapshots both hands' compass IDs before
local prediction can consume a one-count player head or replace a stack. The
snapshot is bound to the exact returned use packet, then consumed only after
that packet reaches `Connection.send`. Snapshotting never starts a reading.
The prediction wrapper's `finally` discards unused bindings after cancellation
or exceptions; nested predictions retain separate identity bindings. World,
connection, player, hand and lobby must still match. Disabled/no-compass use
does not allocate a prediction snapshot, and unsnapshotted packets cannot borrow
the hand's later state. This corrects a source-supported placement boundary bug;
the supplied live log does not prove that this placement path caused its failure.

The particle hook observes `ClientPacketListener.handleParticleEvent` after
vanilla handling, on its game-thread path. Happy-villager packet centres form
bounded candidate sequences starting near the use position. Count and offsets
change the particles' rendering, not the server-supplied packet centre, so they
do not discard the packet. A coherent pass qualifies after 25 samples, or after
three matching points in a repeat. The reading closes after 500 ms without a
nearby particle, or at the next sent use once a pass qualifies. Five seconds is
a timeout, not a minimum wait. Repeated attempts while a reading is incomplete
do not erase it or extend its timeout.
After recording a first ray, further sends within eight blocks preserve that
ray instead of starting an empty reading that hides the useful moved use.
After solving, repeated sends within eight blocks of the second use preserve the
waypoint without extending its expiry. Move farther or clear observations to
begin another solve; changed visible target context still invalidates it.

Multiple coherent candidates received during the reading cause rejection,
including a competitor whose repeat is incomplete. Completed candidates do not
block a later candidate at the same start. Repeated passes along the same axis
within the fitted allowance count as one trail. Packet or candidate overflow
also causes rejection rather than silently losing evidence. Short unconfirmed
trails, curved/backtracking samples, parallel rays, backward intersections,
excessive skew, implausible Hollows coordinates and wide uncertainty produce
guidance without a waypoint. Observations/results expire after five minutes.

The two fitted rays use a covariance axis and closest forward approach in 3D.
Uncertainty includes sample deviation, a small coordinate allowance, crossing-angle
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

Receiving a footer packet does not prove absence of scent. Blank, unrelated,
header-only, loading or incomplete counted data remains unknown when no verified
list-entry source is available. Positive scent rows establish presence. Absence
requires a recognized Active Effects section with explicit no-effects/zero-count
data or disclosed effect rows; a declared count must match the distinct parsed
rows. Loading or truncation markers invalidate absence even when a declared count
matches the disclosed rows or explicit no-effects text is present. Independently
verified list data and positive scent rows remain usable. Countdown changes do not alter presence. Loss of reliable data invalidates
pending and solved Goblin contexts and prevents a new solve until data returns.
The [pinned effect-widget reference](https://github.com/SkyblockerMod/Skyblocker/blob/a3f25d798b5ea8d8a91a690232bbd4274b4a7a3a/src/main/java/de/hysky/skyblocker/skyblock/tabhud/widget/EffectWidget.java)
supports counted widget details and the footer's explicit no-effects branch;
footer God Potion summaries alone do not establish absence of King's Scent.

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
cannot be attributed with certainty; the own-use window, proximity and coherent-sequence checks reduce accidental
matches, rather than establishing ownership. A foreign-only trail, or a
competitor starting after the responsive reading has closed, cannot be ruled
out by the protocol. Waiting five seconds cannot establish originating-player identity.
The [pinned Skyblocker reference](https://github.com/SkyblockerMod/Skyblocker/blob/a3f25d798b5ea8d8a91a690232bbd4274b4a7a3a/src/main/java/de/hysky/skyblocker/skyblock/dwarven/WishingCompassSolver.java)
confirms the separate crystal rows and nucleus exclusion. NEU also checks the
tab footer for scent. The current Skyblocker source also reads every happy-villager packet centre,
completes at 25 samples and uses a 500-ms processing-delay check. Its use
callbacks can return `FAIL`, which short-circuits later Fabric listeners;
observing sent packets avoids that ordering dependency and excludes attempts
cancelled by another mod. No implementation from either reference is copied or bundled.

The supplied live log loaded eviemod `1.4.0.0` on Minecraft 26.2 with Skyblocker
`6.10.4+26.2` and recorded Skyblocker solutions from uses two seconds apart. It
contained no Eviemod compass diagnostics or raw particle metadata, so it cannot
establish which old gate failed. Old fixtures missed callback coexistence,
packet centres with nonzero offsets/counts, repeated attempts and an eight-block
baseline at realistic target distances. The old fixed five-degree crossing guard
also contradicted the eight-block guidance. Numeric parallelism and excessive
computed uncertainty are now rejected instead.

Each enabled reading writes a bounded `[Wishing Compass]` summary to `latest.log`:
start region, received packet count, largest count, nonzero-offset presence,
longest pass, repeats and result/guidance. Unsupported visible prerequisites are
logged explicitly. `diagnosticReport()` additionally describes activation,
metadata readiness and current/last captures for the shared diagnostic command.
It excludes raw tab, player/chat text and item data; no per-particle log, saved
location, upload or networking is added. If solving still fails, retain the
summary after a same-region pair plus visible Eviemod status. Those distinguish
missing use activation/particles, short/ambiguous trails, prerequisites and
intersection failure without blind repetition.

Unit fixtures cover observation lifecycle, geometric rejection, triangulation,
uncertainty and default-off persistence. The shared code is compiled and tested
for Minecraft 26.1.2 and 26.2. CI's offline client fixtures load the packet mixin
and render the shared settings. Offline results do not validate current live
Hypixel particle timing, item uses, target selection or crowded-lobby attribution.
Regressions include the independent review's crystal-format collision and
competing-trail replay, acquisition/placement, missing data, nucleus boundaries,
footer-only scent changes, countdown stability, world-bound footer snapshots,
sequential/interleaved competitors during the reading and same-trail repeats.
New regressions cover the two-second cadence, 500-ms quiet boundary, a 25-sample
pass without repeats, held attempts before/after qualification and solution,
packet-centre semantics and a distant target
at an eight-block baseline. Live success with the corrected build remains a
separate check.
Pre-prediction identity regressions cover a consumed one-count player head,
exact packet identity, both hands, canceled/exceptional sends, nested cleanup,
world/connection/player/lobby changes and bounded overflow.
