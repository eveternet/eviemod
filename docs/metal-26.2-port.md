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

## Implementation checkpoints

The initial split shares Java utility sources and one native build between fixed
Loom projects. The first adapter compilation found three 26.2 API differences
(two BackendCreationException constructors and one nested type annotation).
This is a work-in-progress checkpoint; tests/fixtures for 26.2 are still being adapted.
