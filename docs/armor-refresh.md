# Armor refresh and missing item data

## Observed behavior

SkyBlock armor can temporarily arrive at the client with a stripped-down set of item data. Your tests associated this with movement; stopping or opening inventory often restored the full data. This is an observation, not a guaranteed trigger.

The item ID can remain while its UUID disappears. Paintbrush normally uses the UUID to find your saved dye, so the old lookup fell back to the item's original color.

Captures alone do not establish whether Hypixel or another client mod strips the data.
## Capture findings

Boots lost their UUID, custom name, all 31 lore lines, Mythic tooltip style, enchantment data, gems, reforge, rarity upgrade, potato count, and upgrade level.

Serialized component JSON shrank from 6,257 to 1,075 bytes (about 83%). This measures JSON size, not network bytes or permanent item loss.

The same UUID and missing data returned. SkyBlock's MYTHIC lore vanished; Minecraft's separate rarity stayed COMMON. Gaps reached 6.625 seconds, so 250 ms and 500 ms workarounds were insufficient.
## Dye continuity

Paintbrush remembers the last valid UUID for each local armor slot, without a timeout, while the Minecraft item type and SkyBlock item ID remain the same.

It reads the current saved dye through that identity on each render. Animated dyes continue, and editing or clearing the saved dye takes effect immediately.

Worn armor uses copied stacks. Since 1.5.6, a separate render hook verifies the local player and armor slot before applying the remembered dye to an owned copy.

No live item data or server state is modified. The user confirmed the 1.5.6 fix works.
## Limits and recovery

An empty slot, different item type/ID, absent custom data, malformed UUID, or player/world change clears the remembered identity. A valid new UUID takes precedence. An item never seen with a valid UUID is not guessed.

Two same-type pairs with missing UUIDs can be indistinguishable during a swap. The previous dye may remain until the new UUID returns or the slot is cleared.

If that happens, stop moving or open inventory and allow full data to return. If necessary, unequip/re-equip the boots to clear slot memory, then allow their UUID to return. These are recovery steps, not guarantees.

This workaround covers dye rendering; name/model overrides still require a valid UUID.
## Rarity backgrounds

Rarity continuity is enabled by default. The source configuration retains `rememberRarity` for compatibility; the UI exposes only enable/disable, shape and opacity. This document is source-only and is not bundled as an in-game reference.

Backgrounds appear in real container slots and the nine main hotbar slots while on SkyBlock. Fake slots, cursor items and arbitrary previews are excluded. Rarity follows Skyblocker: pet data first, then lore, then tooltip style; never Minecraft rarity or a local custom name. Confirmed UUIDs and local slot identity bridge missing lore and UUIDs. No live item components are changed.

Player/world changes clear all rarity memory. Changed/empty local slots clear their remembered identity. An item never observed with rarity is not guessed from its item ID. Same-type, same-ID swaps without UUIDs can retain the old rarity until full data returns.

A player/world change clears session rarity memory. Setting `rememberRarity` to false in `config/eviemod.json` and reloading disables continuity. If using Skyblocker too, disable one mod’s rarity backgrounds to avoid drawing both.

Source reference: EquipmentColorContinuity, WornArmorOwnership, EquipmentLayerRendererMixin, RarityMemory.
