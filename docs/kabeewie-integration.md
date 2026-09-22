# Kabeewie integration (eviemod 4.0.0, Minecraft 26.1.2)

The audit below describes the original merge. In 4.0.1, the requested settings correction
moves Paint Brush and SkyBlock Visuals under Appearance, renames Party Commands to Chat
Commands, and removes the Garden pest workflow, its controls and native Loadouts binding.
Soul Whip Fix is a single visual toggle. Migration logic and saved values are unchanged;
retired pest values are retained only as inert compatibility data. See
[the current settings notes](settings-ui.md#merged-settings-correction-401).

## Reference and behavior boundary

The supplied `kabeewie-unified-1.2.0.jar` has SHA-256
`5ea2abfd0c03aaf5f5b519ced24b993d0cc0d5c5fe5a0b6cddb1819f701f3e16`.
The matching local `modidea/build/libs` release has the identical hash; its source archive
has SHA-256 `955283381c3ab254b64d1c89d34f124b8db7eabc7e6e8691aa9408473038e6c2`.
Feature source was imported from that archive. The bundled Soul Whip MIT notice is retained.

The feature algorithms, events, cooldowns, detection heuristics, channel policies and mixin
injection points are preserved. A source comparison confirmed the following 14 files differ
only in package names and configuration access: FarmingToolDetector, GardenDetector,
GardenCommandKeys, GardenKeyMappings, PestWorkflow, MouseHandlerMixin,
ItemInHandRendererMixin, CustomCommandHotkeys, PartyCommandController, SkyblockContext,
VisualHealthController, ClientPacketListenerMixin, GuiMixin and WorldBorderMixin.
GardenToolsClient only changes its settings-screen destination and configuration access.
The combined entrypoint's initialization and tick work now runs from eviemod's entrypoint.

In particular, the existing independent Garden and SkyBlock detectors are retained; replacing
them with eviemod's HM API detector would change when the imported features activate.
The existing health attribute/packet handling, border hooks, broad main-hand animation fix,
party command responses, and always-running pest workflow are intentionally retained.
This merge does not change those behaviors or add a pest enable toggle.

As of 6.0.1, all feature toggles default to off, including Soul Whip Fix, barrier
suppression, ten-heart scaling, custom hotkeys and every party-command channel.
Fresh installs, missing fields and UI resets use these defaults. Explicit saved choices
(including legacy imports) remain unchanged. Plot defaults to 1; custom hotkeys start
empty and native Garden bindings start unbound.

## Shared settings and storage

All mod-owned active configuration is under `config/eviemod/`:

| File | Contents |
| --- | --- |
| `settings.json` | Existing main settings, `features.skyblock`, `features.garden`, `features.soulWhip`, and `migrations` |
| `paintbrush.json` | Per-item model overrides |
| `colors.json` | Per-item dyes |
| `names.json` | Per-item names |
| `helmet-skins.json` | Per-item helmet skins |
| `hypixel-pack.json` | Hypixel resource-pack state |

Existing root-level eviemod files are copied atomically if their new destination does not
exist; the older skyshitter filenames are the fallback for per-item files. Destination
files always win. No original is deleted or changed. Copy failures are handled at each
store's existing load boundary, so other stores can continue. Imported resource-pack
assets remain in `resourcepacks/`, which is Minecraft's resource storage rather than a
parallel configuration system.

Dandelion/MoulConfig's standard controls show SkyBlock Visuals (including Soul Whip Fix),
Garden Tools, Party Commands, and Command Hotkeys in the existing shared screen.
All 24 party-command/channel switches, arbitrary custom hotkey rows, keyboard/mouse
bindings, commands, add/remove actions, both Garden switches, plot number and four Garden
bindings are represented. `/kabeewie`, `/kabeewie settings` and `/gardentools` open that
shared screen. The old screen and both legacy configuration classes are absent.

`DraftKeyMapping` is a small public-API adapter for Dandelion's maintained key editor.
Its native superclass stays unbound so draft command bindings do not enter live key
routing. Standard key capture/reset controls replace the old custom capture UI.
Add/remove saves and rebuilds the same shared settings page, preserving its parent.

Garden keeps its original native key identifiers for compatibility with Minecraft's
Controls screen. Its legacy native options are imported into `features.garden.keys`
before the Garden migration marker is written. On subsequent starts eviemod applies its
own saved keys. Minecraft may mirror the live bindings in its ordinary options file, but
that mirror cannot overwrite existing eviemod settings on startup. Controls-screen edits
are saved back to eviemod. The migration itself only reads Minecraft's options file.

## One-time migration and conflicts

Markers are independently stored as `migrations.skyblock`, `migrations.garden` and
`migrations.soulWhip`. A completed group is skipped before opening any legacy file.
An absent group is left eligible for a later import. Saving a different group does not
materialize unchosen imported defaults as if they were explicit eviemod settings.
Explicit UI choices are recorded even when equal to a default. Resetting a settings map
removes its cleared entries from the saved file, so old channel choices cannot return on reload.

| Group | Legacy source and fields |
| --- | --- |
| SkyBlock | `kabeewie/config.json`: `noBarrierEffects`, `maxTenHearts`, `commandHotkeysEnabled`, `commandHotkeys`, `partyCommands` |
| Soul Whip | `kabeewie/config.json`: `soulWhipFix` → `features.soulWhip.enabled` |
| Garden | `kabeewie/config.json`: `garden`, otherwise `garden-tools.json`; native `key_key.gardentools.*` bindings in Minecraft's options file |

The supplied combined mod first reads standalone Garden settings, then replaces the entire
Garden object when the combined file has one. Migration preserves that precedence,
including original defaults for omitted combined Garden fields. Plot numbers are clamped
to 1–24 as in the source. Missing feature toggles default to off; malformed types
reject the affected group rather than silently coercing or replacing its settings.

Existing explicit eviemod fields win recursively, including `false`, default-valued fields,
individual channel switches and an explicitly empty hotkey list. Hotkey lists are atomic:
an existing eviemod list wins as a whole because the legacy slots have no stable identity
that would permit safe per-row merging. Unknown source fields are not imported as active
settings. Unsupported legacy native key encodings leave Garden eligible for retry.

Each group is validated in a separate candidate. The values are atomically saved first;
only after that succeeds is the marker saved. Failed data writes publish neither candidate
values nor a marker. Failed marker writes leave valid imported data without a marker;
retry treats those persisted keys as authoritative, so newer choices cannot be overwritten.
Malformed existing eviemod settings block writes and retain the last valid in-memory state.
Malformed source groups, failed writes and failed markers are logged, with no added
player-facing migration diagnostics. Malformed combined JSON cannot safely be split into
groups, so all groups still needing that source remain pending until it is repaired.

Legacy `kabeewie/config.json` and `garden-tools.json` are never written, renamed, moved or
deleted. The old JARs must not run alongside eviemod: Fabric conflict declarations prevent
duplicate handlers/mixins from Kabeewie, Garden Tools, Soul Whip Fix or SkyBlock Visuals.

## Verification and limits

`ImportedFeatureMigrationTest` covers fresh installs, all three groups, one/two groups,
late-arriving groups after restart, disabled values, every supported setting, destination
conflicts, once-only behavior, unchanged source bytes/timestamps, malformed source and
destination data, partial older objects, both Garden source layouts, actual filesystem
failure, injected data-save failure, injected marker-save failure, native Garden key migration,
unchanged native options, and explicit choices equal to defaults.

`MergedFeaturesSmokeTest` is an offline client fixture using the actual generated
MoulConfig options and standard key-capture component. It loads all five imported mixin
targets, exercises settings save/reload, channel toggles, plot choice, keyboard/mouse keys,
and hotkey add/remove while checking parent-screen return. Screenshots are written under
`run/merged-fixture/screenshots`. The fixture is excluded from the release JAR.

Run `./gradlew build` for the unit suite. The UI fixture uses
`./gradlew runClient -PuiSmokeTest -PmergedCapture`; it expects a fresh isolated
`run/merged-fixture/config/kabeewie/config.json` with Soul Whip, hearts, barrier effects and
custom hotkeys disabled, one hotkey, and Garden plot 19. This setup never touches the
user's PrismLauncher instance.

Local tests and exact source parity do not establish live Hypixel behavior or interactions
with the user's complete modpack. Live verification still needs a SkyBlock session for
health/Rift transitions, borders, party/guild/co-op commands, pest cooldown/loadout flow,
Garden mouse locking and first-person animation behavior.

## Command hotkey input (4.0.2)

Custom command hotkeys retain sampled physical key/button state while screens own input,
while disabled, and while disconnected. A held key cannot become a new press just because
chat closes. Fabric ScreenEvents samples state at screen initialization and removal as well
as ticks; removal catches a character pressed after the last chat tick immediately before
Enter. Commands still execute through the existing tick path, only on an up-to-down edge in
gameplay. New/rebound slots baseline the current state instead of firing a held binding.
Bindings, command text, and command dispatch are unchanged. This applies to every screen,
without guessing which widgets accept text. As before, polling can miss a complete physical
release/press between samples; no input is buffered or replayed after a screen closes.

Regression tests cover held chat keys, between-tick closing, immediate fresh gameplay presses,
ordinary holds, disabled/disconnected input, changed bindings and independent mouse bindings.
These are local input-state fixtures, not live SkyBlock command execution tests.
