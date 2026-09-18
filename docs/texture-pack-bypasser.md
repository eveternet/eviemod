# Hypixel Pack (3.0.1, Minecraft 26.1.2)

The new shared-settings category defaults off, including old configurations and resets.
Enabling installs the official SkyBlock pack in the active instance's resource-pack directory.
The player selects and orders it through Minecraft's normal Resource Packs screen. eviemod
never changes pack selection, options, priority or server pack preferences. Disabling stops
checks/interception and leaves the local pack and the player's selection intact.

## Discovery and files

`https://api.hypixel.net/v2/resources/packs` was checked on 2026-09-18. It supplies a
SkyBlock deployment ID and versions keyed by integer `packFormat`, SHA-1 `hash` and `url`.
The running game's client-resource format major is matched exactly; there is no nearest-format
fallback or hardcoded pack version/URL. Download URLs must be HTTPS on
`resourcepacks.hypixel.net` under `/SkyBlock/`; redirects fail closed.

New installations use the fixed filename `eviemod-hypixel.zip`. Existing installations retain
their recorded `eviemod-hypixel-<UUID>.zip` path (a one-time UUID, not a hash or version),
so their Minecraft selection ID and ordering remain intact. Updates replace the same path
after temporary-file validation; hash and deployment version live only in configuration. `config/eviemod/hypixel-pack.json` stores that filename, deployment ID, format,
URL, hash, last attempted check and pending reload. The worker verifies SHA-1 on startup/checks;
the packet adapter checks file identity, size and modification time without blocking on hashing.
SHA-1 verifies the API's content identity, not independent authenticity (HTTPS supplies that).

API bodies are limited to 1 MiB and archives to 256 MiB, with connect/request deadlines.
Downloads go to a temporary file in the pack directory. SHA-1, ZIP structure and bounded JSON
`pack.mcmeta` are validated before atomic replacement. Only the recorded file may be replaced;
symlinks, initial collisions and externally modified files are preserved. A deleted managed
pack can be reinstalled. There is no non-atomic replacement fallback. Unsupported filesystems
retain the old file and report failure. Temporary files are removed on ordinary failures;
a killed process can leave an inert `.tmp` file.

Pack replacement and state replacement are individually atomic, not a cross-file transaction.
A crash or disk failure between them can leave a new archive with old state. Hash verification
then disables interception and refuses to overwrite the mismatch; normal server handling
continues. Remove the managed ZIP (not unrelated packs) and use Check for updates to recover.
Malformed state is preserved and logged, never silently reset.

## Checks and reloads

One serial worker prevents overlapping requests. Automatic attempts are persisted before HTTP,
at most once per rolling 24 hours, including failed attempts across restart. Manual checks
bypass this deadline. Disabled installations do not start network work; an already started
check may finish. Failures leave existing packs and vanilla login available.

An unchanged hash causes no download/reload. Changed packs retain their filename and selection.
Background updates set a persistent pending flag without reloading. Each HM API location event
whose structured server type is `SKYBLOCK` is an entry opportunity (including island switches).
If the managed pack is selected, the client refreshes discovery and reloads once; unselected
packs need only discovery refresh. Manual checks apply a pending update immediately, including
one previously downloaded automatically. Successful application clears only the matching hash;
failed reloads retain the pending flag for a later entry. Resource-manager pack instances detect
when startup or a player-initiated reload already loaded the replacement, avoiding a second
reload on entry. No local pack is forcibly activated.

## Packet boundary and limitations

`HypixelServerPackMixin` targets `ClientCommonPacketListenerImpl.handleResourcePackPush`,
after `PacketUtils.ensureRunningOnSameThread`, shared by configuration and play listeners.
The server address must be `hypixel.net` or a subdomain. Packet URL and hash must match the
validated API-installed pack exactly. Only then are ACCEPTED, DOWNLOADED and SUCCESSFULLY_LOADED
sent for that request ID, without pushing anything into Minecraft's server-pack manager.
This acknowledges the local substitute even if the player chooses not to select it.
Unknown requests, changed deployments not yet checked, unsupported formats, proxies/custom
hostnames, missing/corrupt files and incomplete initial setup retain vanilla handling. The
adapter does not cancel unrelated pop-all operations or remove already-active server packs.
Enable/setup before connecting for normal operation without the server-pack reload animation.

Regression fixtures cover discovery, persistence, attempts, integrity failures, interruptions,
file ownership and safe replacement. Local compilation/fixtures do not prove live Hypixel
acceptance, server-transfer behavior or visual reload behavior. Recheck the packet hook and
resource-pack format API on Minecraft upgrades; recheck API schema and request identity if
Hypixel changes its deployment behavior.

## Local validation

`./gradlew build --offline` passed compilation and regression tests.
`./gradlew runClient -PuiSmokeTest -PpackCapture --offline` passed the offline Minecraft
fixture: the new category has exactly the enable toggle and update action, renders with the
feature off, and the common packet listener loads with the mixin applied. The fixture captures
`run/screenshots/texture-pack-bypasser.png` and exits without joining a server. This is not a
live Hypixel login test.
