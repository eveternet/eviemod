# Paintbrush helmet skins and PNG imports

## Scope and persistence

Both `helmetSkins` and `customTextures` default to false in new, missing-key, and reset settings. Dandelion's shared Paint Brush category provides explicit toggles. Opening the editor through that category saves the settings draft so newly selected toggles take effect immediately. Existing per-item choices are never discarded when toggles change.

Helmet skins are separate UUID-to-model mappings in `eviemod-helmet-skins.json`, using the existing validated atomic model store. They take precedence over regular model overrides only for player heads and equipment explicitly assigned to HEAD. Imports never change a server stack, its profile, equipment, stats or item behavior. Missing UUIDs and unavailable imported resources retain normal rendering. Malformed mapping files block writes until successfully reloaded.

Item PNGs become standard item definitions and models in `resourcepacks/eviemod-paintbrush`, under the reserved `eviemod_imported` namespace. Item sprites are stored under `textures/item/` to be discovered by the vanilla item atlas. The model identifier includes its preset and normalized PNG SHA-256, so later imports cannot replace another item's texture. Bow inherits `minecraft:item/bow` transforms; sword and generic handheld inherit `minecraft:item/handheld`. One PNG is a static image, including while drawing a bow. No draw frames, animation metadata, remote upload, or custom model-file import is inferred from a flat image.

The desktop file chooser uses Minecraft's bundled LWJGL TinyFD. Dragging one file into the active import tab follows the same pipeline. Image validation happens off the render thread, checks PNG decoding, byte limits and dimensions before writes, and strips metadata. A 64 × 32 skin is padded to a 64 × 64 UV canvas; only the head and outer head layer are rendered. Individual generated files are replaced atomically, with the item definition last. A successful resource reload precedes changing the UUID mapping. Failed imports preserve the previous mapping. Imported files remain available if the original source is moved or deleted.

## Minecraft 26.1.2 boundary

`TextureImportClient` isolates the native chooser, public resource-pack repository, options persistence and resource reload. The generated pack metadata targets format 84, matching the bundled Minecraft version. Pack selection is saved without calling `Options.updateResourcePacks`, because that method starts a reload without returning its future; the importer awaits exactly one explicit reload instead.

`ImportedTextures` writes the vanilla `minecraft:head` special renderer with kind `player` and the player-head item transform. In this version the special renderer prefixes its texture with `textures/entity/`, whereas the profile texture codec prefixes only `textures/`; the worn profile therefore uses `entity/` explicitly. Tests decode generated item definitions through Minecraft's real codecs.

`HelmetSkinRenderMixin` changes only `LivingEntityRenderer.extractRenderState`'s detached head state. It selects the normal player-skull renderer with a resource texture patch, without replacing a live PROFILE component. `HelmetSkinArmorMixin` skips only a helmet armor piece with an active, available skin, avoiding a second armor shell. Normal item rendering uses the existing item-model lookup mixin. These hooks and profile codecs must be rechecked on Minecraft upgrades.

## Catalog provenance and limits

The 119-entry catalog was extracted from [NotEnoughUpdates-REPO commit bf411c6e466e8fe36f117e8338349d17fd97c180](https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/tree/bf411c6e466e8fe36f117e8338349d17fd97c180/items). Entries were selected from skull skin items whose lore explicitly describes application to a helmet, goggles, mask, head, crown or hat. Duplicate display names were disambiguated with the target from that same lore. The file contains names, item IDs and the Minecraft texture hash decoded from each item's texture property; it does not bundle downloaded skin images. This snapshot does not claim a complete or future-proof list.

Selecting a catalog skin downloads only its texture from `https://textures.minecraft.net/texture/<hash>`, with bounded size, timeouts, and no redirects. Imported user PNGs are never uploaded. Animated Hypixel skins use the static catalog preview texture; server animation timelines are not reproduced. Skin imports can also be used without network access.

Local checks cover opt-in defaults, config validation, generated definitions, head texture paths, size limits, source-file deletion, and component immutability. The optional development fixture exercises local file-drop imports and resource reloads. These checks are separate from live Hypixel and other-mod/resource-pack compatibility testing.
