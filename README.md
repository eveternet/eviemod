# eviemod

Client-only Fabric mod for **Minecraft 26.1.2**, Java 25, Fabric Loader 0.19.5+, and Fabric API 0.155.2+26.1.2 or a compatible newer release for 26.1.2.

Install `build/libs/eviemod-2.1.1+26.1.2.jar` and Fabric API in your instance's `mods` directory. Remove the old Skyshitter JAR when upgrading. Skyblocker is not required. Dandelion (with MoulConfig), YACL, Fabric Language Kotlin, and HM API are bundled; Mod Menu is optional.

On first launch, old `skyshitter-paintbrush.json`, `skyshitter-colors.json`, and `skyshitter-names.json` files are copied to their `eviemod-*` equivalents. Original files are preserved, and existing eviemod files are never overwritten.

## Settings and rarity backgrounds

Open `/eviemod` (or `/eviemod settings`), or use eviemod's Configure button in Mod Menu. The screen uses [Dandelion](https://github.com/AzureAaron/Dandelion) with its MoulConfig backend, matching the configuration framework in the supplied Skyblocker version. It provides a searchable category sidebar and collapsible option groups. The Paint Brush category has an **Open editor** button. `/paintbrush` opens that same standalone editor directly. Its panels use MoulConfig’s renderer, with matching dark surfaces and cyan selection accents.

Rarity backgrounds default to square with 45% opacity. Appearance offers enable/disable, shape and opacity. Settings save when closing and persist in `config/eviemod.json`; `/eviemod reload` reloads that file. Malformed files are preserved and must be fixed before settings can be saved. Paint Brush customizations retain their explicit Apply/Reset controls. See [settings architecture](docs/settings-ui.md) for adding categories, groups and custom editors.

Background visibility follows the supplied Skyblocker 6.10.2+26.1.2 JAR's vanilla UI hooks: ordinary container/inventory slots and the nine main hotbar slots, only while Hypixel's location API reports the `SKYBLOCK` game type. There is no global GUI-item hook. Cursor-held items, floating/snapback renders, fake slots, offhand hotbar items, and arbitrary previews do not get backgrounds. The development fixture has the same local-development exception as Skyblocker.

Rarity lookup also follows that version: PET items use valid `petInfo` and the tier-boost item; other items search lore bottom-to-top, then fall back to `hypixel_skyblock` tooltip styles. Lore can have menu instructions after the rarity and does not require a SkyBlock ID. UNKNOWN/invalid pets produce no background, and pet failures do not fall through to lore. Custom names and vanilla rarity do not determine the background. No additional menu-title blacklist is invented. See `docs/rarity-visibility.md` for the reference audit and exact compatibility limits. If using Skyblocker too, disable its rarity backgrounds to avoid drawing both.

The session remembers confirmed rarity by UUID (up to 2,048 items), and by local inventory/equipment slot while the same item type and SkyBlock ID remain. This bridges stripped lore/tooltip styles and local UUID gaps without a timeout, but only inside the eligible SkyBlock slot rendering paths. Invalid pet data cannot be bypassed using remembered rarity. Empty slots, changed item types/IDs, invalid identity, stack count changes, recognized replacement UUIDs, and player/world changes invalidate the relevant slot memory. Full returning rarity metadata immediately wins. UUID-less foreign stacks and detached copies cannot borrow local slot memory; no rarity is guessed globally from an item ID. Nothing changes live item data or packets.

Limits: an item must first be observed with rarity data. A same-type, same-ID, UUID-less swap without an observable empty slot remains ambiguous, so a prior rarity may remain until identity returns. UUID-less container copies without established local ownership cannot be recovered. Continuity is automatic by default; its implementation and limitations are documented in [the source reference](docs/armor-refresh.md). Skyblocker's own custom backpack/profile/storage screens use explicit Skyblocker rendering calls; eviemod does not inject into those other-mod screens.

## Use the editor

Run `/paintbrush` to open the editor with the held item selected when it has a valid SkyBlock UUID. Otherwise, the item picker opens automatically. **Done** returns to the screen that opened the editor. **Choose item** opens an inventory-shaped picker with the main inventory, hotbar, and equipped items. Opening it takes a fresh inventory snapshot. Empty slots and items without a SkyBlock UUID are disabled. Use **Choose item** to pick another item.

- **Model:** type an Identifier to see inline completions from active resource packs. Up/Down navigates all matches; Tab or Enter accepts; Escape dismisses the suggestions. Click a suggestion to choose it. Unqualified paths such as `diamond` complete across namespaces. There is no separate model browser.
- **Dye:** search the 66 bundled Hypixel dye presets, select a swatch, or enter an RGB hex color. Type to search; Up/Down navigates and Tab/Enter selects. Animated presets have live swatches and previews. The tab is disabled for undyeable items and replacement models without dye tinting (such as vanilla netherite armor).
- **Name:** type a name, highlight characters, then use Bold, Italic, Glitch (obfuscated text), Underline, or Strike. Only the highlighted characters change. To apply a solid color or gradient to the selection, enter a start color and optional end color, then click **Apply color**. Select all to style the entire name. Formatting controls are disabled without a selection. Existing whole-name styles and gradients become editable character styles automatically; insertions and deletions retain styling on unchanged text.

An empty model override retains the original item model. The header shows the item and its draft name, without UUIDs or a preview label. Selected tabs/styles have a filled accent state; keyboard focus uses a background change with no corner outlines. There are no hover tooltips or routine success messages; save errors appear inline. Apply saves the current tab; Reset immediately clears that tab’s saved customization. Drafts survive switching items and tabs; closing asks before discarding unapplied edits. The editor follows Minecraft’s GUI scale, shrinking when required to fit with margins, and leaves the global setting unchanged.

The model, dye, name, info and reload commands remain available as shortcuts. Debug and help commands have been removed. Hold the SkyBlock item in your main hand:

```
/paintbrush info
/paintbrush set minecraft:diamond_sword
/paintbrush clear
```

`set` saves a model Identifier for that specific item's UUID. `clear` restores normal model selection immediately. The commands are registered only on the client.

Helmet skins and custom models are applied per item; there are no extra enable toggles.

- **Skin:** select a helmet or player head, search the bundled Hypixel helmet skins and press Apply, and choose a colour/variant when available. Custom skin uploads are not offered. Its item and worn appearance update without reloading resource packs. Reset restores its original appearance.
- **Model:** choose an existing model, or select Bow, Sword, or Handheld and **Import PNG & apply**. A strip-shaped PNG prompts you to choose its `.mcmeta` file; nearby files are never read automatically. You can choose metadata from any folder, drop the PNG and metadata together, or explicitly use a static image. Imports appear as “Uses a custom model”; Reset clears the model. Uploaded textures are disabled for armor; standard model changes such as gold to leather remain available, including dyeing the leather appearance.

PNGs are limited to 1024 × 1024 and 4 MB. Held-item imports reload the generated resource pack once. Animated sprites support frame order, timing and interpolation; bow drawing stages are not generated. Keep `resourcepacks/eviemod-paintbrush` to retain imports after restarting; source files can be moved or deleted. Skin choices are stored in `config/eviemod-helmet-skins.json`; item models use the existing per-UUID model file. Applied skin names and variants reappear when you reopen the editor. The bundled skin list is a snapshot, and animated helmet variants use a representative frame. See [skin and texture implementation notes](docs/paintbrush-imports.md).

Mappings persist in `config/eviemod-paintbrush.json` in your Minecraft instance. You can also edit this file to configure a UUID directly, then run `/paintbrush reload`:

```json
{
  "d320530e-052d-48fa-bf56-09c2e1a4a12d": "minecraft:diamond_sword"
}
```

Use an **item model Identifier**, as in the `minecraft:item_model` component. For example, `mypack:custom_sword` refers to `assets/mypack/items/custom_sword.json` in an active resource pack; it is not a texture PNG path or a `models/item` file path. The resource pack must supply the model. An unavailable model uses Minecraft's missing-model rendering. Models still evaluate against the original item's properties, so assigning a bow model to another item does not give it bow behavior.

## Dye colors

Hold a UUID-bearing dyeable item in your main hand:

```
/paintbrush color set #FF88CC
/paintbrush color set Pure Black Dye
/paintbrush color set Rose Dye
/paintbrush color info
/paintbrush color clear
```

Six hex digits with or without `#` are accepted, including black (`#000000`). Dye names (with or without “Dye”) and IDs such as `DYE_ROSE` are also accepted, case-insensitively; commands offer dye-name completions. Named presets persist as IDs in the same color file, alongside existing hex values. Animated presets loop through the bundled sampled colors at 100 ms per frame using a shared clock, so inventory and worn armor stay in phase. This is local playback, not synchronization with the server’s animation phase. Colors persist by UUID in `config/eviemod-colors.json`. `/paintbrush reload` reloads name, color, and model files. Replace the old eviemod JAR when upgrading; existing model mappings remain compatible.

The local color overrides the dye tint for normal item rendering and worn leather armor. The original `DYED_COLOR` component and tooltip remain unchanged. Models/resource packs must use Minecraft's dye tint for item colors to appear; fixed-color textures do not become tintable. Model and color overrides are independent, with separate clear commands. Items without a UUID and non-dyeable items keep their normal colors. Dyeable equipment includes leather armor, leather horse armor, wolf armor, and items in Minecraft’s dye-removal tag. The bundled catalog contains 42 static and 24 animated Hypixel dyes from the [NEU dye definitions](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/blob/4828ec7c4a05010e112df8b01f447ded0d487b8d/constants/dyes.json), retrieved September 16, 2026. Fairy armor palettes and vanilla crafting dyes are excluded. Future dyes require a catalog update; there are no runtime downloads.

## Armor refresh continuity

The observed armor-data gaps, local dye continuity workaround and rarity-memory limits are documented only in [docs/armor-refresh.md](docs/armor-refresh.md). No help screen, help command or packaged bug-reference resource is included. Debug commands and recording remain removed.

## Local item names

Hold any SkyBlock item with a UUID and use:

```
/paintbrush name set My favourite bow
/paintbrush name info
/paintbrush name clear
```

Enter the name directly, including spaces; quotation marks are unnecessary and are treated literally. Names contain 1–256 text characters, with no line breaks or legacy formatting codes. The command creates a plain name; use the editor for styling and gradients. Clearing restores the original display name.

Names persist in `config/eviemod-names.json` as UUID-to-styled-name mappings. Legacy UUID-to-string name files load automatically; saving upgrades them to the styled format. `/paintbrush reload` reloads names, models, and colors independently. Existing model and color files remain compatible. Names affect the normal `ItemStack.getHoverName` display lookup, including tooltips and selected-item labels. Server-written chat, scoreboard text, and third-party displays that read raw components are outside this hook. `CUSTOM_NAME`, lore, stack serialization, and the server's item remain unchanged.

## Rendering and item safety

For held-item models, the mixin replaces the `ITEM_MODEL` lookup result inside `ItemModelResolver.appendItemLayers` and its hand-swap animation property lookups. GUI, hotbar, containers, held items, dropped items, item frames, and nested item rendering that use the normal resolver receive the override. Helmet skins pass a detached stack copy with the skin profile to the vanilla head renderer. Third-party renderers that bypass Minecraft's resolver are outside this hook. Worn armor uses a separate rendering system for models; the color feature hooks `DyedItemColor.getOrDefault`, which is used by both item tinting and equipment rendering.

The UUID comes from `minecraft:custom_data.uuid`, with a nonempty SkyBlock `id` in the same component. Missing, blank, malformed, or unconfigured UUIDs preserve the original result. Separate items of the same SkyBlock type remain separate; copies with the same UUID share the customization. The supplied Juju sample matches this format.

No live stack component is changed, including temporarily. Paint Brush has no packet hook or server command. The rarity feature subscribes to Hypixel location updates through HM API to determine whether the player is on SkyBlock; it never changes item packets. Normal rendering passes the original stack to the model. The editor renders a detached preview copy with draft components; this copy never enters the inventory or networking system. The preview omits custom data so saved overrides cannot mask the draft, which can limit previews for resource packs with custom-data-dependent predicates. All configuration is local. Invalid configuration preserves the original file; load failures block saving the affected file until a successful reload.

The provided Skyblocker JAR was inspected as a behavioral reference. This implementation has no Skyblocker dependency and uses a narrower renderer hook rather than overriding general component access.

## Build and verify

With Java 25 selected:

```
./gradlew build
```

Output: `build/libs/eviemod-2.1.1+26.1.2.jar` (the sources JAR is not the installable mod).

Automated tests cover UUID extraction, missing and malformed UUIDs, distinct UUIDs, unchanged stack components, changed stack data, persistence, clearing, and malformed config preservation. Color tests also cover all four leather armor pieces, opaque black, hex validation, non-leather exclusions, persistence, and unchanged dye components. Name tests cover persistence, clearing, validation, fallback, styling, Unicode gradient endpoints and interpolation, selection ranges in either direction, edits preserving formatting, styled-space persistence, legacy migration, autocomplete matching, suggestion click routing and acceptance, dropdown bounds and focus, and unchanged stack components.

Validation performed for 1.5.7: `build` passed all 49 tests. New cases cover render-state player/slot ownership, UUID-less worn copies, data immutability, and rejection of foreign, mismatched, malformed, or stale copies. Coverage includes 72,001 successive UUID-less updates, animated dye playback after an hour of simulated clock time, live dye edits/clears, replacement UUIDs, empty/changed slots, reset behavior, foreign-copy isolation, component immutability, and item data continuity. The development client started and completed resource loading with the new equipment-layer mixin and no mixin errors. The user confirmed the worn-copy fix works in-game on September 17, 2026. Earlier user captures confirmed that UUID/data loss can persist for several seconds, exceeding the old timeout approach.

A separate development-only fixture mod is available with `./gradlew runClient -PuiSmokeTest`; it opens the editor with sample items without connecting to a server, adds a Paint Brush test button to the title screen, and is not packaged in the release JAR.

In-game acceptance checklist: assign a model to a UUID-bearing item; inspect inventory, hotbar, first and third person, a chest, a dropped entity, and an item frame; compare another item of the same type with a different UUID; reload resources; restart the client; clear the override. For name overrides, check tooltip titles and selected-item labels, restart persistence, and clearing. For color overrides, also check the equipped armor on the player, opaque black, and color clearing. Confirm each normal rendering context updates while actual item data remains unchanged (the name override intentionally changes the displayed tooltip title). Test with your active resource packs and other rendering mods.

## Validation for 2.0.0

The build passed all 50 tests. Tests cover rarity parsing, long UUID/lore gaps, independent item identities, foreign-copy isolation, invalidation, component immutability, configuration persistence/error recovery, and legacy save migration, alongside the retained paintbrush tests. The development client successfully rendered square/circle rarity fixtures and the YACL settings screen. `./gradlew runClient -PuiSmokeTest` opens the fixtures; add `-PuiCapture` to run the current interaction fixture, save screenshots under `run/screenshots`, and exit automatically. Live Hypixel data-loss behavior still needs confirmation with the user's mod/resource-pack combination.

## Visibility correction in 2.0.1

2.0.0 incorrectly hooked every GUI item render and used a different rarity lookup. 2.0.1 replaces that with the supplied Skyblocker version's container and main-hotbar call sites, SkyBlock gating, and pet/lore/style lookup order. Continuity remains an intentional addition and cannot expand render eligibility. The fixture displays each item as a real slot, generic preview and fake slot side by side to verify the distinction.

## Validation for 2.1.0

The build passes all 55 tests. The development client renders the MoulConfig shell and embedded Paint Brush editor. Its automated interaction fixture exercises model completion, name input, category switching with preserved drafts, and the unapplied-edit close confirmation. Screenshots are saved under `run/screenshots`. These checks use local fixture items, not a live Hypixel session.

Earlier references to YACL as Skyblocker's main configuration UI were incomplete: the supplied version uses Dandelion, which supports both YACL and MoulConfig. eviemod now selects MoulConfig explicitly.

## Editor correction in 2.1.1

Paint Brush opens as a separate compact editor again, both from its command and the standard Dandelion **Open editor** button. The embedded custom-option adapter has been removed. The editor and item picker reuse MoulConfig's panel renderer; selected tabs use cyan text and an underline. Existing model/dye/name editing, explicit Apply/Reset, drafts and discard confirmation remain.

The UI fixture checks model completion, name entry, tab switching, picker return, and closing back to settings. Local fixtures do not establish live SkyBlock compatibility.
