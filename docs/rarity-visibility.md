# Rarity visibility reference audit

Reference: the user-supplied `skyblocker-6.10.2+26.1.2.jar`.
SHA-256: `55387cb035a992aa06fb838e2a7d10216392fcc189be070301a3b67afc48932b`.
The findings below come from that JAR's bytecode and injection annotations, not the current upstream branch.

| Rendering context | Reference behavior | eviemod 2.0.1 |
| --- | --- | --- |
| Ordinary container slot | AbstractContainerScreenMixin: before `GuiGraphicsExtractor.item(ItemStack,int,int,int)` inside `extractSlot`, gated by `Utils.isOnSkyblock()` | Same method and invocation; draws the original slot stack at slot x/y |
| Fake container slot | Uses `fakeItem`, so the above injection is not reached | Excluded |
| Main hotbar | GuiMixin: invocation ordinal 0 of `Gui.extractSlot` inside `extractItemHotbar`, gated by `Utils.isOnSkyblock()` | Same invocation; all nine main slots |
| Offhand hotbar | Later extractSlot invocations; not ordinal 0 | Excluded |
| Cursor/floating/snapback items | Outside the slot injection | Excluded |
| Generic GUI previews (including Paint Brush) | No background injection into GuiGraphicsExtractor.item itself in this JAR | Excluded |
| Other game modes, lobbies, other servers, release singleplayer | `isOnSkyblock` false | Excluded; location update serverType must be `SKYBLOCK` |
| Local development environment | Utils.updatePlayerPresence permits local/no-world development | Same development-only exception for fixture verification |
| Skyblocker's own backpack/profile/storage screens | Those classes explicitly call ItemBackgroundManager | eviemod does not inject into another mod's custom screens |

There is no menu-title blacklist in the supplied rarity adder. The base `onScreenChange` is empty. The other adders (Jacob medals and legacy attributes) have their own behavior; they are not rarity rules.

## Rarity determination

`ItemRarityBackground.getColorKey` calls `getSkyblockRarity`, which delegates to `ItemUtils.getItemRarity`. UNKNOWN is not drawn.

1. Empty/AIR: UNKNOWN.
2. Custom-data `id == PET`: parse `petInfo`. Invalid or absent data gives UNKNOWN without falling back. Valid pet tier determines rarity; `PET_ITEM_TIER_BOOST` advances the enum by one.
3. Other items: search every lore line in reverse order using `SkyblockItemRarity.containsName`. The first line containing a rarity wins. Within a line, the last matching enum value wins (UNCOMMON over COMMON; VERY SPECIAL over SPECIAL). It is a case-sensitive substring check, not a final-footer regex. A custom-data item ID is not required.
4. If no lore match exists, a `hypixel_skyblock` tooltip style's uppercased path uses the same contains-name lookup. This version does not map `supreme` to DIVINE. Unknown names remain UNKNOWN.

Vanilla RARITY and local display names are not rarity inputs. No additional guesses based on item IDs are made.

## Deliberate additions and limits

The requested missing-data continuity is additional to Skyblocker. eviemod caches previously confirmed non-pet rarity by UUID and local slot identity. The ordinary scope and enable/opacity checks run before this cache is consulted, so memory cannot draw in an otherwise excluded context. Invalid PET metadata is not treated as a recoverable lore gap. Memory is cleared on world/player changes and leaving SkyBlock.

eviemod retains its own square/circle rendering, opacity settings, and classic rarity colors. This correction is about eligibility and rarity lookup, not copying Skyblocker assets or its other background features. A missed Hypixel location update leaves the release feature disabled until a valid update arrives; unlike Skyblocker, eviemod does not send the `/locraw` recovery command.

## Verification

Unit cases exercise lookup precedence, trailing menu text, items without custom IDs, unknown rarity, pet tier boost, malformed pet suppression, and release/development scope, plus the existing continuity/invalidation tests. The development fixture renders the same stacks through real container slots, generic previews, and fake slots side by side. Only the real slots should have backgrounds. A real Hypixel session remains necessary to confirm server-delivered location events in the user's installed mod combination.
