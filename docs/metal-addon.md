# Experimental Metal addon

Eviemetal is a separately loadable Fabric addon. Install exactly one addon JAR,
matching Minecraft. The main eviemod JAR neither includes nor requires it; the
addon also works without eviemod. Fabric Loader is its only required mod dependency.

| Minecraft | Distributable | Optional supported Sodium |
| --- | --- | --- |
| 26.1.2 | `eviemod-metal-mc26.1.2-0.2.0.0.jar` | `0.9.2+mc26.1.2` |
| 26.2 | `eviemod-metal-mc26.2-0.2.0.0.jar` | `0.9.2+mc26.2` |

## Installation and activation

Use the matching **eviemod-metal-mc26.1.2** or **eviemod-metal-mc26.2** macOS CI
artifact, or build on an Apple Silicon Mac. Place the JAR in `mods/` and restart.
Installing the separate addon preserves the existing activation policy: Metal
starts automatically on supported Macs. Requires Apple Silicon, ARM64 Java 25
and macOS 14 or newer; an x86 JVM under Rosetta is unsupported.

Remove the addon or set `-Deviemod.metal=false` to use Minecraft's normal graphics
backend. The explicit disable override survives updates. The optional existing
`-Deviemod.metal=true` argument still works. On 26.2 the selected vanilla backend
can be OpenGL or Vulkan; Eviemetal preserves Minecraft's preference and retry order.
Do not install both addon variants in one instance, or use the other version's Sodium.

## Rendering and failures

The version-specific adapters implement Blaze3D buffers, textures, samplers,
pipelines, passes, fences, readback and presentation over the shared Metal bridge.
26.2 uses a separate GPU surface, sliced vertex bindings, bind-group layouts,
transient allocations and timestamp query pools. GLSL is translated through
shaderc/SPIRV-Cross to MSL. Both variants bundle matching LWJGL 3.4.1 shader tools
and macOS arm64 natives; Sodium is optional and is not bundled.

Platform/conflict checks run before native loading. Native and shader preflight
finish before modifying the Cocoa view. With vanilla, failed startup cleans up
Metal before Minecraft closes the failed window and retries its own backend
candidates. With active Sodium, unsupported Sodium versions or Metal startup
failures stop descriptively under the existing compatibility policy. There is
no supported switch between backends after Metal resources have been created.
Runtime shader/pipeline failures and GPU errors surface clearly; disable Metal
and restart to return to vanilla.

Iris, VulkanMod, Metallum, MetalCraft and MetalRender retain their existing conflict
checks. Guarded legacy GUI state can surround Blaze3D submissions, but direct raw
OpenGL rendering and arbitrary modpack/shader-pack compatibility are unsupported.
The 26.2 adapter supports one color attachment and vertex binding slots 0–3.
Texture arrays/3D textures, additional color attachments, indirect draws and
interleaved multidraw are rejected explicitly; the pinned vanilla and Sodium
routes use the implemented forms. Timestamp queries require native stage-boundary
counter support. Minecraft 26.2 constructs its timer query pool unconditionally, so
the 26.2 addon rejects devices without those counters during preflight. Vanilla
can retry its ordered backends; with active Sodium the existing descriptive
startup-failure policy applies. The retained 26.1.2 adapter does not acquire this
new requirement. No timestamp values are fabricated.

26.2 allocation limits use the active device's native buffer limit, capped at
Java's addressable buffer size. Matrix vertex attributes, CPU-expanded triangle
fans with pending GPU index writes, and mapped writes requiring a GPU blit inside
an open pass are explicitly unsupported. Small orphaned mapped updates remain
supported. No FPS gain or long-duration soak result is claimed.

## Build and verification

```sh
./gradlew build
./gradlew verifyMetalDistributions
python3 .github/scripts/verify-metal-artifacts.py
MTL_DEBUG_LAYER=1 ./gradlew verifyPackagedMetal
```

A normal root build tests both main-mod targets and both addon targets, and places
exactly two versioned addon JARs in `build/distributions/metal/`. Individual builds
are `:metal-addon:build` and `:metal-addon-26.2:build`; each independently resolves
its fixed Minecraft and Sodium dependencies. The native implementation is built
once by `:metal-addon:buildNative` and packaged by both adapters. Xcode tools and
Java 25 are required for native distributions. Linux builds are Java-only fixtures;
`verifyMetalDistributions` requires the packaged arm64 library.

For either module, `packagedTitleSmoke`, `packagedSodiumTitleSmoke`, `packagedVanillaSmoke`,
`packagedSodiumSmoke`, `packagedDisabledSmoke` and `packagedDisabledSodiumSmoke` stage its distributable JAR in
an isolated fixture directory and exclude the addon's compiled classes and loose
shader-tool JARs from the launch classpath. Sodium fixtures stage the exact pinned
Sodium artifact. Development fixtures, worlds, logs and screenshots are not shipped.
26.2 also has `packagedSurfaceSmoke`, `packagedDisabledVulkanSmoke` and
`packagedPreflightFallbackSmoke`, `packagedPreflightVulkanFallbackSmoke` and
`packagedUnsupportedSodiumSmoke` for surface/presentation and compatibility checks.
The unsupported-version fixture changes only the real pinned JAR's advertised
version; it verifies the packaged guard, not compatibility with another release.
Do not run graphical fixtures concurrently on the same display.

CI runs native tests, packaged surface checks, and serial title/world fixtures
under Metal validation; Linux checks unsupported-host/disable routes, including
Sodium, and both main-mod UI routes. The hosted Apple Paravirtual device lacks
26.2's required timestamp counters. The strict 26.2 title/world tasks fail there;
the CI wrapper identifies the exact capability rejection plus verified cleanup
as **not validated**, rather than accepting it as a rendered lifecycle. A passing
hosted job does not satisfy those final physical-Mac completion criteria.
Physical Apple Silicon evidence is recorded separately in [the 26.2 port report](metal-26.2-port.md).

## Stop Unloading My Resourcepacks on 26.2

SUMR Fabric **1.5.1+26.2** reports an unhandled-backend warning with Metal.
This identifies missing SUMR integration, not failed Metal activation. Disable
SUMR as an immediate workaround, or explicitly enable the bounded adapter with
`-Deviemod.metal.sumr=true` in the launcher's Java arguments and restart.
The adapter defaults to off and only applies to that exact SUMR version on 26.2;
SUMR is neither bundled nor required. Unknown versions retain their own warning.

The adapter implements SUMR's backend interface, reports Metal shader compilation
errors through its normal diagnostics, recompiles vanilla sources on failure,
and uses a no-output shader pair for mod-owned stages with no working vanilla
replacement. SUMR's F3+R hot reload remains its own reload operation. Cache bypass
closes replaced native pipelines, and recovery restores its flags even on failure.
Structural pipeline, lazy attachment-state and GPU errors keep the normal failure
behavior. This does not add general shader-pack or raw OpenGL support.

The installed binary's backend guard, fallback provider and cache-bypass contract
were inspected directly; implementation is independent of SUMR's licensed source.
See [SUMR's upstream project](https://github.com/Enchanted-Games/stop-unloading-my-shaders).
The packaged `SumrTitle`, `SumrDefaultTitle` and `SumrAbsentTitle` fixtures check
the exact optional JAR, explicit activation, absent-mod class loading, default-off
behavior, fallback/recovery pixels, native cache replacement, diagnostics and
actual SUMR hot reload. These fixtures do not validate the entire SkyBlock modpack.

The report records the baseline commit, actual game/Sodium interfaces inspected,
rendering regressions, packaged lifecycle results and limits. These are local
vanilla/Sodium fixture results, with no live SkyBlock or full-modpack claim.
Older implementation evidence remains in [Sodium compatibility](metal-sodium-compat.md),
[terrain optimizations](metal-terrain-tier1.md), [memory audit](metal-memory-audit.md),
[GUI compatibility](metal-gui-compatibility.md) and [shader compatibility](metal-shader-compatibility.md).
Source attribution and GPL licensing are in each addon's `NOTICE.md` and `LICENSE`.
