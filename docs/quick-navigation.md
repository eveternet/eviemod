# Quick Navigation

The feature is off on fresh installs, when absent from existing settings, and
after a settings reset. `/eviemod` → Quick Navigation enables it and opens the
tab editor. The editor is available while disabled. Configuring tabs does not
turn the feature on. Done saves all tab edits and returns to the originating
settings screen; Cancel discards the editor draft. Other settings choices are
preserved when the parent screen subsequently saves.

Fresh settings and resets have **no preset buttons**. The editor's fourteen
positions are empty placeholders; only positions with an icon become navigation
buttons. Add the desired icon, command and optional tooltip/title pattern yourself.
Reset tab clears just the selected position. Existing saved buttons, including
previous presets and user customizations, survive an upgrade unchanged.

To use an actual SkyBlock item as an icon, hold it before opening the editor and
choose **Use held item**. The editor copies the item once, including its SkyBlock
ID, custom-data components, textured-head profile, dye and model information.
The icon persists without needing to keep that item in the inventory. Its item
field shows the underlying Minecraft item ID; the field's tooltip identifies the
captured item. Changing the item field replaces the snapshot with the typed icon.
Vanilla item IDs and component syntax remain available for manually created icons.

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
or new UI dependencies are bundled. No reference shortcuts or default icons are
installed. Component syntax and held-item capture can supply custom heads or
dyed items.

## Boundaries and assumptions

`QuickNavigationSettings` owns persistence/defaults and field validation.
Malformed settings reject loading and preserve the original JSON, consistent
with the shared settings store. Item strings are kept intact across game
versions; unsupported icons omit only their runtime tab. The editor previews
them as barriers and shows the error before saving. Typed icons are parsed into
new local ItemStacks through Minecraft's ItemParser. Captured icons use the
registry-aware ItemStack codec and retain their components in an optional
`capturedIcon` JSON string, capped at 64 KiB per position. Both routes create
independent count-one stacks and never mutate inventory components. ItemStack
serialization is shared by the two supported versions; a future component or
resource-pack change can require recapturing an item. Rendering still depends on
the installed resource pack and any other mod that handles those components;
the snapshot does not bundle its textures or require a remote item catalog.

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
the highlight; they never identify the server or enable the feature. User-entered
patterns are heuristics that may change with Hypixel menus. An empty pattern
never highlights a tab.

Selecting Double click requires two clicks within one second. Single click is
the initial action for a newly configured position. A shared one-second cooldown
survives menu changes. All mouse buttons on navigation tabs are consumed,
including their release; command actions reject carried stacks. Unrelated
inventory input retains vanilla behavior. Disable the corresponding
Skyblocker/SkyOcean feature to avoid overlapping independently installed tabs.

## Verification

Unit coverage checks empty defaults/reset, upgrade preservation, opt-in
persistence, malformed-file preservation, commands/patterns, component icon
parsing, captured SkyBlock identity/profile/dye persistence, independent preview
stacks, bounds, activation gates, confirmation expiry and cross-menu cooldown.
The version smoke fixture exercises the actual MoulConfig entry, editor
typing, invalid-input recovery, tab selection, resize, save/parent return,
runtime rendering and Fabric mouse guards with captured command delivery.
It captures inventory and chest-height layouts on both game targets and is
excluded from distributable JARs. These are offline fixtures, not evidence of
live Hypixel command acceptance or compatibility with a full running modpack.
