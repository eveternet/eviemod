# Settings architecture

## Framework and growth

eviemod uses Dandelion with the MoulConfig backend. The supplied Skyblocker 6.10.2+26.1.2 uses Dandelion for its main settings, with both MoulConfig and YACL backend support. Merely finding bundled YACL was insufficient to identify that architecture.

MoulConfig supplies a scrolling category sidebar, search and collapsible groups. Dandelion's standard public structure is **category → group → option**, with optional ungrouped options. This supports many feature categories and groups, but does not expose arbitrary recursive subcategories. If a future feature needs deeper nesting, extend the backend integration or add a dedicated custom panel; do not encode an unlimited tree assumption in feature configuration.

## Adding a feature

`SettingsCategories` holds a list of category factories. Add a factory there (large features can own separate classes), give categories/groups/options stable `eviemod:` identifiers, and bind options to a draft in `ModSettings.Values`. Use descriptive labels and search tags for terminology users may search. Keep groups focused on a feature. Persist defaults and validation in `ModSettings`, separate from rendering.

`EviemodSettings` creates the settings screen, adapts the validated atomic JSON store, and handles save errors. Ordinary settings save when the settings screen closes. The Paint Brush group under Appearance uses Dandelion's standard `ButtonOption` to open a separate editor, as explicitly requested by the user. `/paintbrush` opens that editor directly.

## Paint Brush editor

`PaintBrushScreen` retains native Minecraft widget input, model/dye/name editing and the inventory picker. The editor returns to its originating settings screen on Done; opening the picker and switching tabs preserve drafts. Apply and Reset retain their existing per-tab persistence behavior. Closing asks before discarding unapplied edits.

`EditorTheme` isolates use of MoulConfig's public panel renderer so the editor and picker match the settings surfaces. Native widgets and the existing autocomplete/selection logic remain in use. There is no custom MoulConfig option or input-coordinate bridge for the editor.

## Command Hotkeys editor

The Command Hotkeys category keeps its existing enable toggle and opens
`CommandHotkeysScreen` through a standard Dandelion button. Like Paint Brush, this
editor uses `CompactScreen` and `EditorTheme`, with a native Minecraft
`ContainerObjectSelectionList`, buttons and text fields. Add/remove mutate only
the affected row; the editor, other row widgets and parent settings screen are
retained. Native list navigation, narration and scrolling remain in use. The
scissor override passes logical editor bounds through the existing viewport
transform instead of clamping them to unscaled GUI dimensions.

Every text edit, key binding, addition and removal immediately updates the live
settings and persists through the validated atomic store. `saveCommandHotkeys`
merges only the bindings into the current stored settings, leaving unrelated
parent draft choices pending. The editor also keeps that parent draft in sync so
closing settings cannot restore older bindings. Back or Escape only returns to
the original settings instance; there is no apply/save step. Write failures use
the existing settings error alert, preserving the editor and its edits for
recovery. Editing never enables Command Hotkeys. Clicking a binding captures a
keyboard key or mouse button; Escape while capturing clears it. The existing
integer key encoding and command strings are unchanged.

The offline version smoke fixture exercises the actual editor on both supported
Minecraft targets: stable rows and screens, text and key input, mouse capture,
unbinding, scrolling, resizing, removal focus, parent draft preservation, and
immediate save/reload before navigating away, including the final empty list.
Store tests cover unrelated settings isolation, retained enable choices, detached
live rows, failed writes and malformed configuration protection. This does not test server command
execution or live Hypixel behavior.

Local verification: both target builds and unit tests passed. Both
`runClient -PuiSmokeTest -PversionSmokeTest` launches passed the hotkey interaction
fixture (`EVIEMOD_HOTKEYS_SMOKE_OK`); their captured editor layouts were inspected.

## Dependency boundaries

The exact Minecraft 26.1 backport is pinned in `libs`; see its README for provenance. The settings store adapter extends `ConfigManagerImpl`, required by this version’s MoulConfig save wiring. Recheck it and the panel-rendering adapter when upgrading. YACL remains a bundled Dandelion dependency, not an alternative selectable eviemod backend.

`./gradlew build` checks parsing, identity, persistence and editor helpers. `./gradlew runClient -PuiSmokeTest -PuiCapture` exercises editor input, picker return and closing back to settings, and captures the settings/editor screens. The fixture maps editor coordinates through the same viewport transform and is excluded from release artifacts.

## Merged settings correction (4.0.1)

The top-level categories are Appearance, Hypixel Pack, Garden, Chat Commands and Command
Hotkeys. Paint Brush and SkyBlock Visuals are ordinary option groups under Appearance.
Soul Whip Fix has no separate group; its original description is available on hover.
The existing rarity controls and Hypixel Pack controls keep their prior editors.

Chat commands retain the same per-command and per-channel configuration keys. A command
group has one Dandelion label description and three compact boolean rows. The pinned
Dandelion BooleanController builder has no compact-row option: its standard MoulConfig
editor wraps every boolean in a full title/description card. `CompactToggleController`
implements the supported, non-sealed BooleanController interface and composes MoulConfig's
maintained RowComponent, SwitchComponent, text, alignment and hover components. It uses
no custom drawing, coordinate interception, reflection or framework patches. This adapter
is limited to the imported visual and chat-command booleans; recheck it on library upgrades.

PestWorkflow and its event registrations/state are deleted. The native Loadouts key mapping
and all pest UI/translation controls are removed. Force Finnegan and Loadouts values are
excluded from Garden migration and discarded from existing eviemod settings on the next save.
Other Garden values and feature algorithms remain unchanged. Completed migration
markers remain unchanged; a legacy source containing only Force Finnegan or a
native Loadouts binding does not set the Garden migration marker.
