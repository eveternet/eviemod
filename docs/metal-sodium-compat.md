# Sodium on Eviemetal

## Supported boundary

Minecraft 26.1.2, Fabric Loader 0.19.5, Sodium `0.9.2+mc26.1.2`, Apple Silicon,
ARM64 Java 25, macOS 14+. The existing `-Deviemod.metal=true` remains the only
opt-in; missing/false stays OpenGL. Sodium is optional and is not bundled.
When Metal is requested on a supported host with Sodium, a mismatched Sodium
version or failed Metal initialization is a fatal, descriptive error. Other
renderer conflicts retain their existing policy. Logs distinguish `Using
OpenGL`, `Experimental Metal backend active`, and `Sodium terrain GPU endpoint
active: Eviemetal/Metal (no GLRenderDevice)`.

Primary references inspected:

* [MetalCraft a2cc827](https://github.com/Im-Fran/MetalCraft/tree/a2cc82780d01a51d00a297f75cf07c221d7c8700), including `compat.sodium.SodiumMetal` and Sodium mixins.
* [Sodium 26.1.2 / 0.9.2](https://github.com/CaffeineMC/sodium/tree/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413), including `RenderSectionManager`, `DefaultChunkRenderer`, `ShaderChunkRenderer`, `UniformBufferManager`, arenas, mapped staging, compact vertices, and translucent sorting.
* The authoritative local Eviemetal memory fixes at `8ef52f4`, checkpointed as `codex/metal-memory-checkpoint-8ef52f4` before implementation.

## Architecture and ownership

Sodium retains CPU meshing, lighting/tint, scheduling, visibility, render lists,
directional slicing, 20-byte compact vertices, translucent sorting, arena
allocation decisions, and batching. `SodiumRenderDevice` implements its existing
command-list boundary instead of constructing `GLRenderDevice`. Its tessellation
object holds explicit arena references without a GL vertex array or ownership of
those buffers. No GL context or second renderer is created.

`SodiumMetal` owns a deliberate mapping from Sodium integer object identities to
current Eviemetal `GpuBuffer` objects. Allocation replaces storage only after the
new allocation succeeds; close uses existing MetalBuffer retirement. Copies,
uploads, and mapping go through the existing command encoder and upload ring.
The mapped staging ring remains enabled: shared Apple memory is coherent and
Sodium fences consumed ranges before reuse. Staging and frame-ahead fences use
the existing Metal shared-event timeline, query actual completion, and fail on
a bounded wait timeout instead of allowing unsafe reuse.

The mutable section timestamp buffer takes ordered Eviemetal writes rather
than Sodium's unfenced permanent mapping. Its borrowed `GpuBuffer` identity never
owns or closes Minecraft storage. A narrow native extension creates an
independently retained `R32Sint` texture-buffer view. Binding refreshes the view
when a small ordered upload orphans buffer storage, allocates the replacement
first, then releases the old view. Already encoded commands retain their native
references. Destruction releases views and removes borrowed identities.

The program bridge collects real Sodium shader sources, applies attribute
bindings, and invokes the existing GLSL → SPIR-V → MSL translator and native
compiler at link time. Shader failures propagate. Current std140 `u_Globals`
buffer slices preserve offsets; loose region offset, region ID, and render time
uniforms use translated offsets. Block atlas, lightmap, and signed section
timestamp bindings are explicit for both shader stages.

Terrain pipelines use the actual pass target, color/depth state, blending,
culling, depth bias, and compact integer/normalized attribute layout. Native
multi-draw consumes Sodium's counts, pointer-sized byte index offsets, and signed
base vertices in their existing order. Pipeline cache keys use structural
attribute values, avoiding an entry per region/tessellation object. Programs own
and release their pipeline/depth/function/default-uniform resources.

Terrain encoders open lazily. If Sodium grows/uploads/maps its quad index buffer
between draws, the encoder closes, the transfer occurs in order, and the target
reopens with load actions. The next draw rebinds pipeline, buffers, textures,
uniforms, and full-target scissor. Ordinary Blaze3D passes retain their own state
setup. World unload removes all bridge buffer/program/view owners; device close
also cleans remaining bridge resources before native teardown.

## Version-sensitive adaptations

Optional, pinned mixins live under `mixin/sodium/`. Their runtime guards preserve
the original GL behavior with Metal disabled. Required injection counts expose
API drift. Four small `SodiumMixinPlugin.postApply` adaptations are necessary
because private handlers from another mixin are unavailable to ordinary
injection, and a constructor redirect cannot replace concrete `GLRenderDevice`
with a different interface implementation:

1. Exact RenderDevice static constructor expression → conditional device factory.
2. Exact three GL frame-fence calls in Sodium's merged Minecraft handlers → Metal timeline adapter.
3. Sodium's GL context/driver check handler returns early only while Metal is active; GL driver/context checks are inapplicable without a GL context.
4. The GL no-error-context option predicate returns false on Metal; other options retain their behavior.

Each boundary checks its expected match count and fails clearly on drift.
Ordinary redirects adapt only Sodium buffers, shader programs, uniforms, and
section texture views. `MetalBufferSodiumMixin` supplies the accessor required
by the current section-time constructor without changing base buffer ownership.
Sodium's GL shader-source workaround is wrapped so its original behavior remains
intact on OpenGL.

Unlike the old MetalCraft bridge, this does not install a fake GL function
provider, resurrect raw buffer allocation/retirement, fabricate capabilities,
or treat fences as always complete. It uses 26.1.2's sliced globals and timestamp
texture, current pipeline/target interfaces, and the authoritative local memory
implementation. Native changes are confined to the timestamp view factory.

## Regression and runtime evidence

Seventeen addon tests pass locally with Metal API validation enabled, including
the existing memory/upload/fence/shader checks. `SodiumShaderTest` translates the
actual pinned shader assets for opaque/cutout defines and compiles MSL on macOS.
`SodiumTerrainDrawTest` verifies pixels for a nonzero base vertex and a 12-byte
index offset, signed timestamp sampling, 40 buffer orphan/view replacement
cycles, and bounded completed allocation growth.

Physical runtime testing uses an isolated instance with only Minecraft 26.1.2,
Fabric Loader, the addon, Sodium 0.9.2, and Sodium's bundled required Fabric
modules. No Iris, extensions, resource/shader packs, or full modpack. The
development-only smoke mod is excluded from distribution and drives the game
autonomously; it creates/reopens seed 424242 and asserts the active backend.

The first complete three-minute lifecycle run reached title and world, showed
opaque/cutout terrain, water and overlapping clear/stained glass, cow and armor
stand, chest/sign/campfire, particles, inventory GUI/model and text, and clouds.
It travelled 320 blocks, changed camera orientation and blocks, rebuilt the
renderer, reloaded resources, resized/toggled fullscreen, changed worlds, and
unloaded/reopened before clean shutdown. A preceding roughly four-minute world
run showed stable warm allocation. The first lifecycle run's allocated Metal
bytes fell to 262,619,136 after final world unload; its dimension and setup
commands were subsequently given stronger assertions for repeat validation.

Two subsequent packaged-JAR lifecycle sessions passed all 21 phases with
`MTL_DEBUG_LAYER=1` on Apple M3 Pro / macOS 27.0.1 / ARM64 Temurin 25.0.3.
Runtime metadata confirmed Minecraft 26.1.2 and Sodium `0.9.2+mc26.1.2`.
The addon code-source assertion identified the actual distributable JAR; its
shaderc/SPIRV-Cross modules came from nested dependencies, with their loose
development classpath entries removed.

The final session additionally drove normal forward input while flying and
rotated the camera across chunk/section boundaries, with sampled positions
changing from x=160 to x=125, then x=320 to x=263. Captured frames were inspected
for opaque/cutout geometry, overlapping glass/water, breaking cracks, GUI/text,
entities/block entities/particles, a lit Nether alcove, and End terrain,
endermen/crystals and boss bar. Both sessions asserted actual Nether → End →
Overworld transitions, resource reload, renderer rebuild, resize/fullscreen,
and world unload/reopen. Each process exited with status zero.

| Packaged session | Final Metal allocated bytes | Final bridge owners |
| --- | ---: | --- |
| First asserted lifecycle | 247,939,072 | buffers/borrowed/programs/pipelines/time views all zero |
| Continuous movement repetition | 245,727,232 | buffers/borrowed/programs/pipelines/time views all zero |

In the final run, terrain pipeline count stayed at three in the Overworld and
two in the Nether, and world allocations decreased after rebuild/reload rather
than growing with each region. These numbers are `MTLDevice.currentAllocatedSize`
after a real completion fence, not process RSS or a promise of zero global atlas,
framebuffer or upload-ring storage. The earlier timestamp regression checks
actual pixels and bounded native allocation across repeated storage replacement.

The packaged addon without Sodium also reached a nonblank Metal title frame and
exited normally. Sodium with the Metal opt-in absent initialized the real Apple
OpenGL device and passed the disabled-backend startup assertion. The complete
project build passed 162 main-mod tests and 17 addon tests; artifact separation
verification passed. No release/tag or push was performed.

Local evidence (ignored/generated, not shipped):
`/tmp/eviemetal-sodium-launch-16.log`, `/tmp/eviemetal-sodium-launch-17.log`,
`/tmp/eviemetal-sodium-off.log`, `/tmp/eviemetal-vanilla-metal.log`, and screenshots
under `metal-addon/run/distribution/sodium-*.png`. The validated JAR SHA-256 is
`58967169a609cca9bb96d9f7fb296524781d095e931d2e28383b26ea3476533c`.

## Limits and reproduction

Only this exact Sodium build is supported. No Iris/shader packs or arbitrary
raw-GL mod compatibility is claimed. The timestamps use ordered uploads; no
performance improvement is claimed or benchmarked. Eight-bit terrain indices
and tessellation patches fail explicitly (the pinned renderer uses 32-bit
triangle indices). The supported fragment interface has one `fragColor` output
at location zero. `getSubTexelPrecisionBits()` reports eight bits for the compact
texture-coordinate bias; this is a Metal terrain policy, not queried GL data.

Screenshots and short local runs are practical visual/lifecycle evidence, not
an exhaustive translucent ordering oracle, a long-duration soak, or live
SkyBlock/full-modpack validation. Future Minecraft/Sodium changes need source
inspection and runtime regression at these pinned boundaries.

Build on ARM64 macOS with Java 25 and Xcode tools:

```sh
MTL_DEBUG_LAYER=1 ./gradlew :metal-addon:build :metal-addon:verifyDistribution
# Put sodium-fabric-0.9.2+mc26.1.2.jar alone in metal-addon/run/mods/.
MTL_DEBUG_LAYER=1 ./gradlew :metal-addon:runClient -Pmetal -PmetalSmokeTest -PexpectMetal -PmetalWorldTest
```

The fixture logs phases, bounded pipeline counts, bridge owners, and native
allocation samples; after a real final GPU fence it requires every world-owned
bridge map empty, prints `EVIEMETAL_SODIUM_LIFECYCLE_OK`, then stops Minecraft.
Without `-PmetalWorldTest`, the fixture checks a nonblank title screenshot and
stops. These assertions/logs are development controls, not player-facing UI.

## Changed source areas

* Addon build/dependency pin and optional mixin registration.
* `MetalSupport` / `MetalBootstrap`: narrow Sodium allowance, version guard and unambiguous backend failure/logging.
* `compat/sodium/{SodiumRenderDevice,SodiumMetal,SodiumFrameSync}`: GPU endpoint, programs, resources, passes, submission and real fences.
* `mixin/sodium/*`: pinned integration boundaries listed above.
* `device/MetalTerrainResources`, `mtl/Mtl`, native `newTerrainTimeView`: narrow signed timestamp view extension.
* Policy, shader and native draw regressions; development title/world/watchdog fixtures.
* This report, addon documentation and attribution notice.

Existing MetalBuffer, MetalTexture, MetalPipeline, MetalCommandEncoder, UploadRing,
native upload/retirement/synchronization, and frame autorelease scopes were not
replaced. Generated worlds, screenshots, logs and build output stay untracked.
