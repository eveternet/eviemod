# Lava to Water

Enable **Appearance → Lava to Water** in `/eviemod`. The main toggle and custom
color tint default to off, including existing configurations, resets, and legacy
imports. The initial tint is `#3F76E4`. Hide Lava Fog defaults to true as a
subordinate option and has no effect while the feature is disabled.

## Reference investigation

The requested NoammAddons feature is named **LavaToWater**, not WaterToLava.
Reference: Noamm9/NoammAddons, branch `26.1.2`, commit
`6d0c95a5e994e9acc1d043c7eb2cdac916809a31`, inspected in the local
`noammandodin/NoammAddons` checkout. That checkout includes a CC0-1.0 license.
The relevant source paths are:

- `src/main/kotlin/com/github/noamm9/features/impl/visual/LavaToWater.kt`
- `src/main/java/com/github/noamm9/mixin/MixinFluidStateModelSet.java`
- `src/main/java/com/github/noamm9/mixin/MixinLavaFogEnvironment.java`

Noamm intercepts `FluidStateModelSet.get(FluidState)` at RETURN. For exactly
`Fluids.LAVA` and `Fluids.FLOWING_LAVA`, it looks up the water model. That brings
over the still, flowing, and overlay materials, water's render layer, and biome
water tint. The nested water lookup exits the lava guard without recursion.
Custom tint constructs a model with those same materials/layer and a constant
color source. Resource-pack water materials are used; no texture files or block
states are replaced.

Noamm also intercepts `LavaFogEnvironment.getBaseColor` to return either
`WaterFogEnvironment.getBaseColor` or the custom tint. This happens while enabled
even if Hide Fog is off; in that case vanilla lava fog distances remain. With
Hide Fog on, its `setupFog` hook sets alpha to zero, moves environmental start/end
to the render distance, and cancels lava's fog setup. Changing settings or
toggling the feature calls `LevelRenderer.allChanged()` to rebuild chunk meshes.

## Eviemod integration

The implementation keeps persistence in the shared validated settings store,
under `features.lavaToWater`. It uses Dandelion's existing boolean and color
controllers in the Appearance category. Malformed booleans, non-integer or
out-of-range RGB values, and null groups are rejected without overwriting the
configuration. No NoammAddons runtime dependency or config import is needed.

`LavaToWaterRenderer` is the small Minecraft rendering adapter. The two mixins
are registered in `eviemod.mixins.json`; their APIs match the locally inspected
26.1.2 bytecode and 26.2 sources. They are shared across both builds.

The client settings tick publishes an immutable volatile options snapshot, so
chunk worker threads do not read mutable UI drafts. A changed active option or
enable/disable transition rebuilds existing chunks on the client thread.
Title-screen edits are safe; a subsequently opened world builds fresh meshes.
Saving through any settings action and `/eviemod reload` are picked up on the
next client tick. Cancelled or unsaved drafts do not affect rendering.

Tinted models are cached by water-model identity and RGB value. New baked models
after a resource reload invalidate the cache naturally. In addition to Noamm's
fog changes, this port sets sky/cloud fog ends to the render distance instead
of leaving them at the zero-initialized values after cancelling vanilla setup.
Render-distance fog fields remain untouched.

This is a global visual feature, as in the reference: lava remains lava for
damage, movement, lighting, fluid ticks, particles, sounds, buckets, and network
state. It does not suppress the fire overlay. Water and non-lava fluids keep
their original models and fog. Other mods that bypass vanilla's fluid-model or
fog paths (including shader/rendering replacements) need separate compatibility
testing; this port does not claim Sodium or Iris compatibility.

## Validation

The shared regression suite covers default/missing/reset settings, explicit
persistence, malformed-file preservation, legacy migration isolation, both lava
types, untouched water/empty fluid models, state preservation, tint materials and
cache invalidation, rebuild decisions, fog guards, and the custom fog-color hook.
The existing offline version smoke fixture now forces both new mixin targets to
load, as well as opening and closing the actual shared settings UI.

Build both targets with Java 25 and `./gradlew build`, then run
`python3 .github/scripts/verify-artifacts.py`. The existing CI launch fixtures run
`:runClient -PuiSmokeTest -PversionSmokeTest` and the corresponding
`:eviemod-26.2:runClient` task. Offline coverage does not establish live Hypixel
or modpack behavior.

Manual game checks: enable beside source/flowing lava; change tint; toggle fog
while submerged; disable and confirm lava visuals return; reload a resource
pack; restart and check persistence. Confirm ordinary water stays unchanged and
lava still damages a survival player.
