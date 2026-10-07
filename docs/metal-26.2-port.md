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

Fresh Loom source resolution on 2026-10-07 confirmed client SHA-1
`2dc72797acbc1b63fc16a11c4ac393605f453754`. The local source archive's SHA-256 was
`42db4f840d3203566bb126b92a58fc07fd474b168c2f82f87514afd92470bc30`.
CI resolves and reports the same client independently; archive hashes can differ
between decompilation runs. `report-metal-upstream.py` saves the actual startup,
surface, device, pipeline/layout, pass, buffer and transient-memory contracts as
review evidence, rather than using copied interfaces as upgrade authority.

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

## Final physical-Mac validation

Physical Apple M3 Pro / arm64 macOS 27.0.1 / Temurin 25.0.3, with
`MTL_DEBUG_LAYER=1`, on 2026-10-07. The cloud continuation was reviewed against the
actual Minecraft and pinned Sodium implementations, followed by fresh root builds
and packaged runs. These physical-Mac results are separate from hosted CI below.

| Check | Result |
| --- | --- |
| Root build, both native distributions, artifact separation | Passed |
| Main-mod tests | 172 per Minecraft target, zero failures/errors/skips |
| Retained 26.1.2 addon tests | 48, zero failures/errors/skips |
| 26.2 addon tests | 49, zero failures/errors/skips |
| Packaged 26.2 surface checkpoint | FIFO/IMMEDIATE clear/readback/present/close passed |
| Packaged 26.2 vanilla and Sodium titles | Panorama/buttons/text screenshots and clean close passed |
| Packaged 26.2 vanilla world route | All 21 phases passed |
| Packaged 26.2 Sodium world route | All 21 phases and arena/timestamp/ownership stress passed repeatedly |
| Packaged 26.1.2 vanilla/Sodium final regression routes | Both passed after split, using nested shader tools |
| 26.2 explicit disable with pinned Sodium | Normal vanilla OpenGL selected; Sodium remained installed |
| 26.2 explicit disable with Vulkan preference | Vulkan selected; original candidate order checked |
| 26.2 injected preflight failure | Native allocation/fence/context cleanup checked before successful OpenGL and Vulkan retries |
| 26.2 unsupported Sodium | Actual packaged version guard rejected a pinned JAR advertising an unsupported version descriptively |

The three-minute world route covers world entry, normal movement/camera travel,
block changes and rebuild, inventory, particles, resource reload, resize,
fullscreen, Nether/End/Overworld assertions, unload/reopen, screenshots and close.
Inspected 26.2 captures show opaque/cutout terrain, glass/water, entities, block
entities, particles and GUI/text. This does not establish an exhaustive transparency
ordering oracle, performance improvement or live SkyBlock compatibility.

Focused 26.2 pixel tests check two vertex bindings, first index/base vertex/first
instance, vertex/uniform slice offsets, blending, reversed depth, render-area clipping,
partial color/depth clears with both D16 and D32 attachments,
timestamp interruption/resumption, texture source origin/stride/destination offsets,
texel-view replacement without rebinding/recompiling, and transient copies across
retirement. Buffer tests repeat GPU resize copies and partial maps twelve times.
Sodium shader tests translate/compile the actual opaque, cutout (`ALPHA_CUTOUT=0.5`)
and translucent (`ALPHA_CUTOUT=0.01`) assets.

The complete port review found a shared partial-clear cache that still keyed depth
as a boolean and created only D32 pipelines. It now keys the actual color/depth
format pair and creates attachment-compatible pipelines; the D16/D32 pixel test
checks both color and depth readback. Hosted 26.1.2 Sodium travel also exposed a
valid empty arena copy reaching Metal's zero-byte blit assertion. The shared JNI
bridge treats an empty copy as a no-op before touching the encoder. Both adapters'
native smoke tests check preserved buffer contents and encoder generation.
These are targeted shared-native corrections; the retained 26.1.2 Minecraft and
Sodium renderer implementations remain unchanged.

The packaged Sodium stress helper uses real upstream arenas: twelve forced growth
runs preserve uploaded segment data, a shared owner relocates with callbacks, and
retired source buffers return with the same identity from the recycler. Three
forced section-time resizes preserve old timestamps and initialize new tails to
`-1`. At two world unloads, the fixture observed **74** then **11** world-owned
buffers, all closed. Labels are fixture-only anchors checked against the pinned
sources; they are not runtime renderer ownership heuristics.

| Controlled 26.2 lifecycle repetition | Final fenced Metal allocated bytes | Framebuffer | Completed fence |
| --- | ---: | --- | ---: |
| Vanilla 1 | 599,474,176 | 1920x1080 | 32,505 |
| Vanilla 2 | 599,523,328 | 1920x1080 | 37,857 |
| Sodium 1 | 170,934,272 | 1920x1080 | 20,739 |
| Sodium 2 | 164,413,440 | 1920x1080 | 32,757 |

These measurements use `MTLDevice.currentAllocatedSize` after completed GPU work,
include global/title/atlas/framebuffer resources. The fixture restores a 960x540
window, waits five seconds, and records the resulting 1920x1080 Retina framebuffer
after the final fence. The comparable vanilla repetitions differ by 48 KiB; the
Sodium repetition decreased. This is bounded local repetition evidence, not a
long-duration leak guarantee or an FPS comparison. Both world ownership checks
passed in each Sodium run, and each device shutdown verified zero allocations,
zero fence state and no live native context.

Those repetitions used implementation `0565d6a`; the subsequent empty-copy fix
was rebuilt and revalidated as `2a849ec`. Its complete 26.2 vanilla/Sodium routes
reported 593,231,872 and 155,910,144 bytes at the same 1920x1080 framebuffer,
with completed fences 20,659 and 30,323 respectively. Both titles, the standalone
surface, all five compatibility routes and native cleanup passed again.

The final local packages tested at `2a849ec` were:

| Package | SHA-256 |
| --- | --- |
| `eviemod-metal-mc26.1.2-0.2.0.0.jar` | `0a0c150262ebc6bc098d807709c9882ae379addb838540d639e9c6893df4fe04` |
| `eviemod-metal-mc26.2-0.2.0.0.jar` | `f92275a8b0e072fefe213cb70edadcbf97bf2376983e7af4a1c0382d2b6322c7` |

Both contain byte-identical arm64 native bridge SHA-256
`bf063c0199d33182b09272b522cc0a98f65732e506171de073b9deb272b51d2a`.
These identify the physical fixture packages; independently compiled CI packages
need not have the same archive/native hashes.

The final retained 26.1.2 vanilla and
Sodium runs reported 259,424,256 and 254,050,304 bytes respectively; the older
fixture does not normalize its final framebuffer for comparison with 26.2.
The retained Sodium Tier 1 assertions passed: 174 layout snapshots, 202,086 batches,
1,414,602 binding attempts and 423,646 actual binding calls.

## Hosted CI

[Implementation CI at 2a849ec](https://github.com/eveternet/eviemod/actions/runs/37600927935)
passed all applicable Linux and macOS jobs. Both native distributions and their
separation checks passed. Linux exercised both actual packaged disabled/unsupported
host routes, including pinned Sodium, the 26.2 Vulkan preference route and both
main-mod UI fixtures. macOS retained all 48 old addon tests and passed both 26.1.2
title/world configurations, including the existing terrain optimization assertions.

The hosted Apple Paravirtual GPU lacks stage-boundary timestamp counters.
Minecraft 26.2 creates its timer pool unconditionally, so native preflight correctly
rejects that device. The 26.2 job passes its independent surface checkpoint and
compatibility matrix, but reports title/world routes as **NOT VALIDATED** with
verified native cleanup. Its one timestamp-dependent pixel test is skipped;
all 49 tests run without skips on the physical M3 Pro. Hosted green CI alone is
not 26.2 lifecycle evidence; the successful physical routes above supply it.

## Evidence locations

Generated evidence stays local and is not distributed or committed:

- `/tmp/eviemetal-final-empty-copy-build.log`: root build/native distributions and 49/48 addon tests.
- `/tmp/eviemetal-final-upstream-contracts.log`: freshly resolved Minecraft source contracts.
- `/tmp/eviemetal-final-route-evidence.jsonl`: repeated physical lifecycles and exact package/native hashes.
- `/tmp/eviemetal-current-26.2-evidence.jsonl`: final shared-bridge packages, surface/titles/worlds and cleanup markers.
- `/tmp/eviemetal-current-26.2-compatibility.log`: disable, unsupported Sodium and OpenGL/Vulkan preflight retry.
- `/tmp/eviemetal-final-26.1.2-vanilla.log` and `/tmp/eviemetal-final-26.1.2-sodium.log`: final retained regression routes.
- `/tmp/eviemetal-surface26.log`: first packaged surface checkpoint.
- `/tmp/eviemetal-world26-vanilla.log`: initial 26.2 vanilla lifecycle.
- `/tmp/eviemetal-packaged-sodium26-1.log`: initial versioned Sodium lifecycle.
- `/tmp/eviemetal-packaged-sodium26-stress-2.log`: arena/time stress and closed-buffer assertions.
- `/tmp/eviemetal-baseline-final-and-fallback-2.log`: nested shader tools, title,
  preflight retry, Vulkan disable preference and final 26.1.2 regression routes.
- `<module>/build/packagedSmoke/<flavor>/`: screenshots, game logs and isolated worlds.

The final fixtures exclude loose shaderc/SPIRV-Cross dependencies and assert
nested dependency code sources throughout titles and complete worlds on both
targets. Earlier fixture/port failures were committed as WIP per project policy
and subsequently corrected; their passing
reruns above do not make the original failed attempts passing evidence.

See [the continuation record](metal-26.2-handoff.md) for the handoff checklist's
outcome and reproducible commands. Known unsupported rendering forms and device
requirements remain documented in [the addon guide](metal-addon.md). No live
SkyBlock/full-modpack validation, release or tag is claimed.
