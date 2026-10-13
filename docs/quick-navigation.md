# Quick Navigation

The feature is off on fresh installs, when absent from existing settings, and
after a settings reset. `/eviemod` → Quick Navigation enables it and opens the
tab editor. The editor is available while disabled. Configuring tabs does not
turn the feature on. Done saves all tab edits and returns to the originating
settings screen; Cancel discards the editor draft. Other settings choices are
preserved when the parent screen subsequently saves.

## Inspected references

The supplied JARs were inspected directly with CFR 0.152:

| Reference | SHA-256 |
| --- | --- |
| Skyblocker `6.10.4+26.2` | `66da04a2f1462210a6786aa0489302666b8c6a83d723124d2cccea93b37a5b55` |
| SkyOcean `1.18.1-26.2` | `d1603b5750bc968f6c4584afb7227d9930aa4c958964d0f1fd14e4cdc61223d8` |

Skyblocker's `QuickNav`, `QuickNavButton`, `QuickNavConfirmationButton` and
`QuickNavigationConfig` establish fourteen creative-style tabs, menu-title
highlighting, component-based item icons, a one-second confirmation window,
and the default menu/warp shortcuts. Its buttons use vanilla creative-tab
sprites as a fallback and its screen mixins handle background layering.

SkyOcean's `ButtonConfigScreen` extends `InventoryScreen`, with a column of
icon, command, title-pattern and tooltip inputs to the left of the inventory.
Clicking a preview tab selects it, with per-tab hide and reset actions. Its
actual editor uses Olympus; it is not a Dandelion screen. eviemod independently
implements this interaction with its existing CompactScreen, EditorTheme,
EditorButton, ModelField and native text boxes. No reference mod code, assets
or new UI dependencies are bundled. Default icons use vanilla items rather
than the references' custom textured heads. Component syntax can supply
custom heads or dyed items.

## Boundaries and assumptions

`QuickNavigationSettings` owns persistence/defaults and field validation.
Malformed settings reject loading and preserve the original JSON, consistent
with the shared settings store. Item strings are kept intact across game
versions; unsupported icons omit only their runtime tab. The editor previews
them as barriers and shows the error before saving. Icons are parsed into
new local ItemStacks through Minecraft's ItemParser, never live inventory
components.

`QuickNavigationClient` uses Fabric's screen initialization, extraction and
mouse events, with one accessor for the container's four geometry fields.
There is no general Screen rendering injection. Inactive tabs clip the four
pixels that would sit under the inventory border; highlighted tabs extend
across that seam. Hit areas exclude those four inventory pixels. ContainerScreen
has the reference's one-pixel bottom-border correction; recipe-book shifts
and resized screens use the current accessor bounds. The shared adapter is
compiled independently for Minecraft 26.1.2 and 26.2.
If the current GUI scale leaves less room than the container plus its tabs,
the window clips the outer tabs, as with the supplied reference. eviemod does
not change the player's global GUI scale or move tabs over inventory slots.

Runtime activation requires an enabled feature, a current authenticated
Hypixel connection, confirmed SkyBlock context, and a non-creative player.
The location API is preferred, with the existing scoreboard context as a
fallback while its location event arrives. Menu title patterns affect only
the highlight; they never identify the server or enable the feature. Default
patterns come from the supplied reference and are heuristics that may change
with Hypixel menus. An empty pattern never highlights a tab.

The default island/Garden/Hub/Dungeons Hub warps require two clicks within
one second. Other shortcuts use one click. A shared one-second cooldown
survives menu changes. All mouse buttons on navigation tabs are consumed,
including their release; command actions reject carried stacks. Unrelated
inventory input retains vanilla behavior. Disable the corresponding
Skyblocker/SkyOcean feature to avoid overlapping independently installed tabs.

## Verification

Unit coverage checks defaults, reset, opt-in persistence, malformed-file
preservation, commands/patterns, component icon parsing, independent preview
stacks, bounds, activation gates, confirmation expiry and cross-menu cooldown.
The version smoke fixture exercises the actual MoulConfig entry, editor
typing, invalid-input recovery, tab selection, resize, save/parent return,
runtime rendering and Fabric mouse guards with captured command delivery.
It captures inventory and chest-height layouts on both game targets and is
excluded from distributable JARs. These are offline fixtures, not evidence of
live Hypixel command acceptance or compatibility with a full running modpack.
