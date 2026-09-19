# Noamm score-sync v1 (Eviemod 4.1.0)

## Contract and optional boundary

The user-supplied Eviemod v1 interoperability specification and Java/JVM addendum
are the external contract. No NoammAddons or Odin repository or implementation
artifact was inspected. Test classes under `src/test/java/com/github/noamm9` are
minimal contract fixtures, not upstream source, and are never release inputs.

`ScoreSyncClient` checks Fabric Loader for `noammaddons` at `CLIENT_STARTED`.
Only then does `NoammScoreHook` read the public static `NoammAddons.isLoaded`
field. If false, absent or incompatible, the integration remains unavailable
for the session; there is no readiness polling. Per the addendum, mod client
initialization is complete at this lifecycle point, so this read does not force
Noamm startup. No websocket connection-status check is needed or attempted.

All Noamm member access is isolated in `NoammScoreHook`. In the absence of a
compile-time API artifact, public reflection calls the exact documented
`EventListener(Class, EventPriority, boolean, Function1)` constructor with the
exact `WebSocketEvent$Payload` class, `NORMAL`, and `false`, then `register()`.
This avoids fabricated compile-time stubs in production and adds no dependency.
It does not call synthetic/reified helpers or internal bus registration APIs.
The callback reads only `EventContext.getEvent().getMessage()` and returns Kotlin
`Unit`. The listener is retained, reused across websocket reconnects, and
unregistered at shutdown. API errors disable the bridge without failing startup.
There is no networking, authentication, token, or Odin state access.

The setting lives in the existing Chat Commands category, is absent when the
hook is unavailable, and defaults to false on fresh/missing/reset configuration.
Both its UI setter and runtime gate enforce availability. A saved explicit true
choice remains saved while Noamm is absent, but cannot activate the relay.
Legacy migration does not populate this new field.

## Occurrences and timing

`ScoreRelay` is a client-thread state machine with an injected monotonic clock,
timer and normal command sender. Strict JSON accepts only string `type` values
`dungeonmimic` and `dungeonprince`. Each packet creates its own pending occurrence.
A timer is scheduled for 1,000 ms and dispatches back onto the client executor.
It never sends early. Like any Minecraft client task, actual transmission can
be later if the client thread is stalled; exact wall-clock execution cannot be
guaranteed. No background thread reads or mutates Minecraft state.

Chat cancellation strips legacy code pairs and uses `^Party > .*?: (.+)$` with
only `Locale.ROOT` lowercasing of the captured body, exactly the ten contract
bodies, and no trimming or approximate body matching. The observation window
is `[receipt, receipt + 1000 ms)`. A match cancels all same-kind windows containing
that observation. Earlier/later messages and other kinds do not suppress events.
Canonical sends are `pc Mimic Killed!` and `pc Prince Killed!` through the existing
Minecraft command transport. No per-run state is retained.

Only the existing Hypixel-server gate is required to receive/send; no `/party`
query or inferred membership restriction is added. Party absence is left to
normal server failure handling. Settings disablement, disconnect, level change,
and integration failure invalidate pending work and retries. The scheduler is
created lazily on the first supported, enabled event.

## Dungeon context limitation

Eviemod previously had Hypixel/SkyBlock/Rift detection, but no dungeon/floor API.
`DungeonChatContext` reads the current sidebar at chat observation time rather
than guessing from Noamm packets or tracking dungeon runs. It accepts only the
location line `The Catacombs (F1..F7)`, `(M1..M7)`, or `(E)`, optionally prefixed
with `⏣ `, after legacy formatting removal and outer whitespace stripping.
That whitespace handling is location-only, never applied to party detection.
Mimic cancellation requires numeric floor 6 or 7; Prince requires a recognized
dungeon location. Unknown/changing scoreboard data does not cancel a relay.

These location fixtures are a documented Hypixel display heuristic, not proof
of equivalence to Odin's `DungeonUtils.inDungeons` during every server transition.
Live floor/entrance/master-mode and scoreboard-transition validation remains
required. No Odin implementation or private state is accessed.

## Send failures and correlation limitation

The transport has no request IDs or typed chat send results. Only received
non-overlay system chat can trigger cooldown classification. Full-line matching
keeps quoted player messages from being classified as failures. The small
`ChatCooldown` adapter recognizes command-rate limiting (one-second fallback)
and a duration-bearing chat rate limit (milliseconds rounded up):

- Command response evidence: a player's direct report on the
  [Hypixel forum, March 2025](https://hypixel.net/threads/you-are-sending-commands-too-fast-please-slow-down.5879639/).
- Duration-bearing response evidence: a player's direct report on the
  [Hypixel forum, April 2018](https://hypixel.net/threads/chat-small-chat-filter-improvement.1645158/).

These are observed reports, not stable server API guarantees or current live
fixtures. No reusable error constants were supplied by the contract or found
in Eviemod. Unknown formats never produce a retry. Repair this adapter if actual
Hypixel responses change; do not broaden it to arbitrary messages containing
“cooldown”, “wait” or “muted”.

First sends and retries await responses in send order for at most five seconds.
A cooldown consumes the oldest outstanding attempt and schedules one retry only;
the retry flag belongs to the occurrence. A recognized own party echo retires
the oldest corresponding attempt. Other outgoing chat/commands abandon outstanding
response attribution, preventing a later foreign error from triggering a relay
retry. An unclassified non-party system line clears outstanding
response tracking, conservatively covering mute and generic failures. No response,
a thrown send error, or an unclassified error ever schedules a retry.

FIFO response order, the five-second response window, and own-echo sender parsing
are conservative attribution assumptions; server chat provides no proof tying
an error to a specific command. Unrelated system lines may therefore prevent an
otherwise eligible retry. Sends bypassing Fabric's outgoing hooks, reordered
responses, and unusually delayed responses remain ambiguous. This cannot be made
fully reliable without a correlated transport API. The one-second announcement
observation windows are independent of response tracking and are not canceled by
unrelated system chat.

Sender websocket echo behavior remains unspecified. Every observed supported
Payload is treated as an incoming occurrence. No local-origin assumptions,
network fallback, second protocol, Bat support, or run bookkeeping are added.

## Validation scope

Deterministic tests cover strict packet parsing/Bat exclusion, both canonical
messages at the one-second deadline, all accepted announcements, exact whitespace
and prefix rules, dungeon/floor gating, overlapping and repeated occurrences,
window boundaries, default and explicit retry delay, retry exhaustion, mute and
generic failure abandonment, foreign send/echo/timeout attribution, transport
exceptions, lifecycle clearing, absent/unready/incompatible Noamm, exact listener
class and public registration, persistence defaults and malformed preservation.
Fixture registration is not live Noamm testing. No claim is made that these unit
tests exercise Noamm's actual binary or live Hypixel chat failures.

The real offline Minecraft 26.1.2 client smoke test passed without Noamm installed:
startup completed, fresh configuration defaulted off, the shared settings UI
exposed no score-sync control even with an in-memory saved true choice, and no
relay timer thread started. Run it with
`./gradlew runClient -PuiSmokeTest -PscoreSyncSmoke`; it uses the isolated
`run/score-sync-fixture` directory and exits after its assertions. Test fixtures
under `src/test` are not included in this client run. The release and sources
JARs were also inspected and contain no `com/github/noamm9` fixture entries.

## Changed files

- Runtime: `src/main/java/dev/eviemod/features/scoresync/ScoreSyncClient.java`,
  `NoammScoreHook.java`, `ScoreRelay.java`, `DungeonChatContext.java`, and
  `ChatCooldown.java` in that same package.
- Initialization: `src/main/java/dev/eviemod/features/skyblock/ImportedFeatureClient.java`.
- Shared settings: `src/main/java/dev/eviemod/paintbrush/ImportedFeatures.java`,
  `ImportedFeatureMigration.java`, and `ImportedSettingsCategories.java`.
- Regression tests: `src/test/java/dev/eviemod/features/scoresync/ScoreRelayTest.java`,
  `NoammScoreHookTest.java`, `DungeonChatContextTest.java`, `ChatCooldownTest.java`,
  and `src/test/java/dev/eviemod/paintbrush/NoammScoreSettingsTest.java`.
- Contract fixtures: `src/test/java/com/github/noamm9/NoammAddons.java`,
  `event/Event.java`, `event/EventContext.java`, `event/EventListener.java`,
  `event/impl/WebSocketEvent.java`, and `event/priority/EventPriority.java`.
- Offline client fixture: `src/uiTest/java/dev/eviemod/paintbrush/NoammAbsenceSmokeTest.java`
  and its dispatch in `UiSmokeTest.java`.
- `build.gradle`: one minor release bump within the existing Chat Commands section
  and an isolated offline smoke-test launch option. No dependencies added.
- This document: `docs/noamm-score-sync.md`.
