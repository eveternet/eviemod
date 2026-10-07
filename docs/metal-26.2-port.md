# Parallel Eviemetal backend port

## Baseline before changing the build layout

Starting point: PR 16 branch `codex/dual-minecraft-builds`, commit
`b1ae1b3fc40f73462becfa166fd4aaa5ce598ddf` (not `main`). Its Eviemetal
implementation is unchanged from `2c866d7304ec118d2a6996e6d741efc92254227c`.
The main mod already has independent 26.1.2 and 26.2 targets.

Fresh checks on 2026-10-06, physical Apple M3 Pro, arm64 macOS 27.0.1,
Temurin 25.0.3, `MTL_DEBUG_LAYER=1`:

- 26.1.2 addon build and native distribution verification passed.
- All 48 addon tests passed, zero failures/errors/skips. These include native
  smoke, pixel draw/readback, shader translation, terrain binding/layout reuse,
  buffer lifetime and cleanup tests.
- Packaged `eviemod-metal-0.1.1.0.jar` without Sodium rendered the title screen,
  produced a varied screenshot, printed `EVIEMOD_METAL_FRAME_OK` and closed cleanly.
- With pinned Sodium `0.9.2+mc26.1.2`, the packaged lifecycle fixture rendered
  terrain, GUI/inventory, particles and block breaking, travelled, rebuilt
  chunks, reloaded resources, resized and toggled fullscreen. It failed at
  its Nether setup command because an unchanged `execute ... run fill` was
  incorrectly considered an error in an already-used fixture world. This is
  fixture evidence, not a renderer crash or a passing lifecycle result.

The fixture now accepts the exact unchanged-block translation keys for redirected
fill/setblock commands too, and can run the same route without Sodium. Subsequent
complete runs are recorded below separately from the unmodified baseline.

## Existing boundaries

- Native: `Mtl` JNI and one Objective-C++ Metal implementation; owns device,
  command buffers, retained resources, Cocoa layer/drawables and GPU completion.
- Minecraft-independent implementation: native loading, platform/activation
  policy, GLSL normalization/translation and disk caching, upload staging ring.
  The ring uses Minecraft's allocation exception but no rendering interfaces.
- Minecraft-facing 26.1.2 adapter: backend/bootstrap, device, command encoder,
  pipelines, passes, resources/queries and startup/legacy GL mixins.
- Sodium-facing 26.1.2 adapter: exact version guard, GL device/program interception,
  terrain vertex layouts, numeric binding plans, timestamp views and lifecycle
  anchors. Geometry storage resolves at draw time, independently of structural
  layout/PSO identity.

## Authoritative 26.2 interfaces inspected

The [NeoForge migration primer](https://github.com/neoforged/.github/blob/main/primers/26.2/index.md)
is a checklist. Interface and startup decisions are checked against the actual
26.2 client JAR and decompiled implementation, not inferred from the primer.

`Minecraft` obtains ordered candidates from `PreferredGraphicsApi.getBackendsToTry()`;
`GpuBackend.createDevice` now receives a critical shader loader and declares
`BackendCreationException`. The window retry loop remains, but `createSurface`
occurs after it. `GpuSurfaceBackend` owns configure/acquire/blit/present/close.
`GpuDeviceBackend` exposes `DeviceInfo`, transient command memory and timestamp
query pools. Draw arguments are now count/instances/first/base/first-instance;
vertex bindings use buffer slices. Pipeline vertex bindings and bind-group layouts
are separate immutable objects; formats and blend operations changed.

The Mojang 26.2 library manifest pins LWJGL **3.4.1**, including shaderc and
SPIRV-Cross. Shader tool dependencies must match that ABI.

Published [Sodium 0.9.2 for 26.2](https://github.com/CaffeineMC/sodium/releases/tag/mc26.2-0.9.2)
source tag resolves to `6c26e7b7eded82ce5a1d27f9b147ce5d8de99b7a`.
Its terrain shaders/pipelines/buffers now use Blaze3D. The old GL device and shader
interception classes have been removed. Its OpenGL draw context still accesses a
GL render pass and loose region/time/id uniforms, so that is a concrete adapter
boundary. Arena ownership/recycling and section timestamp buffers now use
`GpuBuffer` and command copies; tests must cover their replacement and relocation.

These are local vanilla/Sodium fixture checks. No live SkyBlock validation is claimed.

## Baseline rerun

With the redirected-fill fixture correction, the packaged 26.1.2 Sodium route
completed Nether/End travel, unload/reopen and final shutdown. It reported
`EVIEMETAL_SODIUM_LIFECYCLE_OK`, with zero bridge buffers, bytes, borrowed
resources, programs, pipelines, timestamp views and layouts after unload.
Driver-reported Metal memory after the final GPU fence was 239,058,944 bytes;
this includes retained title-screen/game resources, not just terrain.

## Implemented transition

- Fixed `:metal-addon` (26.1.2) and `:metal-addon-26.2` Loom projects with independent
  Minecraft/Sodium dependencies, exact Minecraft metadata and addon version `0.2.0.0`.
- Shared loading/policy, shader normalization/translation/cache, upload ring and JNI
  sources in `metal-shared/`. One arm64 native build serves both distributions.
- Root `build` builds/tests both addons and both main mods. `metalDistributions`
  synchronizes exactly two versioned addon JARs into `build/distributions/metal/`;
  `verifyMetalDistributions` requires native libraries. Artifact separation checks
  validate version names, metadata, shared classes, nested LWJGL modules and arm64 Mach-O.
- 26.2 prepends Metal to the actual vanilla candidate array, preserving its order
  on disable, unsupported hosts or vanilla retry. Native/shader preflight precedes
  Cocoa attachment; the surface is prepared while startup can still retry.
- `MetalSurface` owns configuration, drawable acquisition, blit, present and close.
  FIFO/IMMEDIATE modes use native vsync configuration. Resizing/fullscreen use
  the game's surface configuration boundary.
- Explicit native format, vertex, topology, index, comparison and blend mappings;
  attachment-compatible PSO variants; pipeline-object identity retains all immutable
  pipeline/layout state. Numeric stage binding plans resolve resources outside the
  per-draw name lookup path, and native storage generations invalidate texel views.
- Sliced/indexed/instanced draws, separate multidraw, render-area clipping,
  transient allocation retirement and native timestamp queries implement actual
  vanilla/Sodium forms. Unsupported forms fail explicitly; limits are in the addon guide.
- Sodium 26.2 keeps its Blaze3D pipelines, GL batch submission frontend, arena
  ownership/recycling and section timestamps. Only the draw-context factory and
  loose region/time/id uniform endpoint require a Metal adapter. The old GL-device,
  program-interception and ASM lifecycle rewrites remain specific to 26.1.2.

The actual 26.2 `Minecraft`, `GpuDevice`, `GpuSurface`, command/pass descriptors,
vertex/pipeline/layout interfaces, GUI renderer, shader assets and Sodium source
were inspected. `DrawBackend` chooses its existing separate multidraw frontend
for non-Vulkan devices; `DrawContext.create` is the checked replacement anchor.
Sodium `SingleOwnerBufferArena`, `SharedBufferArena`, `ArenaAggregator`, staging,
`UniformBufferManager` and `SodiumWorldRenderer` were inspected for copies,
recycling, section-time initialization and world deletion. Unique startup/factory
bytecode anchors and every pinned public legacy GL entry point have regression checks.

## Local validation completed before handoff

Physical Apple M3 Pro / arm64 macOS 27.0.1 / Temurin 25.0.3, with
`MTL_DEBUG_LAYER=1`. All results below are local; hosted CI has not yet been verified.

| Check | Result |
| --- | --- |
| Root build, both native distributions, artifact separation | Passed |
| Main-mod tests | 172 per Minecraft target, zero failures/errors/skips |
| Retained 26.1.2 addon tests | 48, zero failures/errors/skips |
| 26.2 addon tests | 42, zero failures/errors/skips |
| Packaged 26.2 surface checkpoint | FIFO/IMMEDIATE clear/readback/present/close passed |
| Packaged 26.2 vanilla title | Nonblank panorama/buttons/text screenshot and clean close passed |
| Packaged 26.2 vanilla world route | All 21 phases passed |
| Packaged 26.2 Sodium world route | All 21 phases passed; a later stress-enabled repetition also passed |
| Packaged 26.1.2 vanilla/Sodium final regression routes | Both passed after split, using nested shader tools |
| 26.2 explicit disable | OpenGL selected for normal preference; Vulkan selected for forced Vulkan preference |
| 26.2 injected preflight failure | Native allocation/fence cleanup checked before successful OpenGL retry |

The three-minute world route covers world entry, normal movement/camera travel,
block changes and rebuild, inventory, particles, resource reload, resize,
fullscreen, Nether/End/Overworld assertions, unload/reopen, screenshots and close.
Inspected 26.2 captures show opaque/cutout terrain, glass/water, entities, block
entities, particles and GUI/text. This does not establish an exhaustive transparency
ordering oracle, performance improvement or live SkyBlock compatibility.

Focused 26.2 pixel tests check two vertex bindings, first index/base vertex/first
instance, vertex/uniform slice offsets, blending, reversed depth, render-area clipping,
timestamp interruption/resumption, texture source origin/stride/destination offsets,
texel-view replacement without rebinding/recompiling, and transient copies across
retirement. Buffer tests repeat GPU resize copies and partial maps twelve times.
Sodium shader tests translate/compile the actual opaque, cutout (`ALPHA_CUTOUT=0.5`)
and translucent (`ALPHA_CUTOUT=0.01`) assets.

The packaged Sodium stress helper uses real upstream arenas: twelve forced growth
runs preserve uploaded segment data, a shared owner relocates with callbacks, and
retired source buffers return with the same identity from the recycler. Three
forced section-time resizes preserve old timestamps and initialize new tails to
`-1`. At two world unloads, the fixture observed **74** then **11** world-owned
buffers, all closed. Labels are fixture-only anchors checked against the pinned
sources; they are not runtime renderer ownership heuristics.

| Completed packaged lifecycle | Final fenced Metal allocated bytes |
| --- | ---: |
| 26.1.2 Sodium final regression | 218,021,888 |
| 26.1.2 vanilla final regression | 212,893,696 |
| 26.2 vanilla initial route | 663,666,688 |
| 26.2 Sodium initial route | 246,841,344 |
| 26.2 Sodium stress repetition | 226,525,184 |

These measurements use `MTLDevice.currentAllocatedSize` after completed GPU work,
include global/title/atlas/framebuffer resources, and are not comparable leak bounds
without controlling window/framebuffer size and repeating the same route. In
particular, the larger vanilla 26.2 number still needs a controlled repetition.
The 26.1.2 final Sodium run also passed the existing Tier 1 assertions: 157 layout
snapshots, 127,627 batches, 893,389 binding attempts and 296,934 actual binding calls.

## Evidence locations and remaining work

Generated evidence stays local and is not distributed or committed:

- `/tmp/eviemetal-packaging-build-5.log`: root build/native distributions and 42/48 addon tests.
- `/tmp/eviemetal-surface26.log`: first packaged surface checkpoint.
- `/tmp/eviemetal-world26-vanilla.log`: initial 26.2 vanilla lifecycle.
- `/tmp/eviemetal-packaged-sodium26-1.log`: initial versioned Sodium lifecycle.
- `/tmp/eviemetal-packaged-sodium26-stress-2.log`: arena/time stress and closed-buffer assertions.
- `/tmp/eviemetal-baseline-final-and-fallback-2.log`: nested shader tools, title,
  preflight retry, Vulkan disable preference and final 26.1.2 regression routes.
- `<module>/build/packagedSmoke/<flavor>/`: screenshots, game logs and isolated worlds.

The later fixtures exclude loose shaderc/SPIRV-Cross dependencies and assert
nested dependency code sources. Final 26.1.2 routes and 26.2 title/retry checks
passed with that stricter packaging path; the complete 26.2 world routes must be
repeated with it on the final pushed revision. Earlier fixture/port failures were
committed as WIP per project policy and subsequently corrected; their passing
reruns above do not make the original failed attempts passing evidence.

The draft PR is a handoff, not completion of the original port criteria.
See [the handoff checklist](metal-26.2-handoff.md) for the remaining validation,
CI and review gates. No release/tag has been created.
