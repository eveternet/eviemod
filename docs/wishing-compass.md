# Wishing Compass solver

Enable **Mining → Wishing Compass Solver** in `/eviemod`. The default is off,
including existing configurations and resets. Manually use a compass, wait for
its trail to finish, move at least eight blocks sideways within the same region,
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
bounded sample sequence starting near the use position. Three matching points
in a repeat pass are required before the first pass can be fitted. Bursts with
randomized offsets, missing repeats, short trails, curved/backtracking samples,
parallel rays, backward intersections, excessive skew, implausible Hollows
coordinates and wide uncertainty produce guidance without a waypoint. A five
second window limits capture; observations/results expire after five minutes.

The two fitted rays use a covariance axis and closest forward approach in 3D.
Uncertainty includes sample deviation, a nonzero angular floor, crossing-angle
amplification and the rays' separation. This cannot prove that both compass
uses had the same server-selected target. Region, Jungle Key presence and
visible crystal/King's Scent tab-list changes reset observations. Other unseen
target changes require **Clear compass observations**. Region checks protect
unsolved observation pairs; a solved waypoint survives crossing region boundaries
while navigating. Scent countdown changes do not invalidate observations. No structure name or
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

Unit fixtures cover observation lifecycle, geometric rejection, triangulation,
uncertainty and default-off persistence. The shared code is compiled and tested
for Minecraft 26.1.2 and 26.2. CI's offline client fixtures load the packet mixin
and render the shared settings. Offline results do not validate current live
Hypixel particle timing, item uses, target selection or crowded-lobby attribution.
Live Hypixel testing must be reported separately.
