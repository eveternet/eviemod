# Disable Unintended Equip Animations

One off-by-default toggle in Appearance → SkyBlock Visuals replaces Soul Whip Fix.
It retains the sole `features.soulWhip.enabled` backing value and option ID
`eviemod:soul_whip/enabled`. Existing true/false choices therefore need no rewrite;
the existing one-time Kabeewie `soulWhipFix` import and migration marker still apply.
Missing values and reset configurations remain off. There is no second toggle or
independent empty-air preference. The historical package and MIT attribution remain.

## Mechanism

The original implementation was checked on 26.1.2 and 26.2. The current 26.3
owner and swing signature are documented in [minecraft-versions.md](minecraft-versions.md).

Inspection of the actual Minecraft client bytecode shows three sources feeding the
same `ItemInHandRenderer.mainHandHeight` equip animation:

- `itemUsed(MAIN_HAND)` resets the height to zero. The existing Soul Whip hook
  continues cancelling this render-only reset; offhand use is unchanged.
- `tick()` lowers the height while the rendered stack differs from the held stack.
  The existing slot-transition tracking stays in place. Same-item metadata updates
  can refresh the renderer's stack without a dip. A valid, unchanged SkyBlock UUID
  establishes continuity; different UUIDs and base item types retain vanilla equip
  transitions. Without UUID evidence, only component-equivalent stacks qualify.
  This tightens the old base-item-only check, which could hide a real replacement.
- `Minecraft.startAttack()` resets `attackStrengthTicker` and `itemSwapTicker` in
  the MISS / now-air block branch. `tick()` cubes `getItemSwapScale(1)` to obtain
  the unchanged main-hand item's target height, causing an extra dip after a miss.

The input mixin only observes the reset branch and the ensuing normal swing call.
It neither cancels input nor modifies attack timers, swing state, swing duration,
packets, or item components. Piercing, block and entity attacks clear the previous
miss marker. Early rejected clicks leave the previous active miss alone.

Only the renderer's single `getItemSwapScale` result becomes 1 during a recorded
miss recovery, with the same player, slot and held item and no slot transition.
The vanilla item-change zero-height branch, offhand heights and hands-busy branch
remain intact. Recovery, disabling, a different player, a slot change or an item
replacement clears the marker. No global attack-cooldown accessor is intercepted.

## Verification boundary

The shared unit suite covers repeated misses, cooldown completion, subsequent
non-miss attacks, disabling, player changes, slot switches, genuine item replacements,
Soul Whip UUID-preserving metadata updates, missing/malformed identity, and legacy
settings/save/reset behavior. Bytecode boundary tests run against both Minecraft
targets to check the reset, swing and render-scale injection sites.

The offline version smoke fixture applies both mixins, checks the single renamed
control and its saved runtime binding, and invokes the transformed main/offhand
`itemUsed` hook. It does not connect to Hypixel. Live Soul Whip use and visual
interaction with other animation mods still require in-game verification.

Local validation on 2026-11-17: both distributable builds passed, with 225 unit
tests per target (450 total), artifact verification passed, and both actual client
launches emitted `EVIEMOD_EQUIP_ANIMATIONS_SMOKE_OK` and `EVIEMOD_VERSION_SMOKE_OK`.

Manual in-game checks with the toggle off and on: use Soul Whip repeatedly, click
empty air with slow and fast weapons, keep swinging after turning toward a block
or entity, scroll hotbar slots during cooldown, replace the selected stack (including
two distinct UUIDs of the same base item), and swap/use the offhand. Confirm the
arm swing and timing are unchanged and ordinary equip transitions still play.
