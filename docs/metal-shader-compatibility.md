# Metal shader compatibility

Eviemetal accepts a bounded subset of legacy **desktop GLSL** supplied through
Minecraft's `ShaderSource`/`RenderPipeline` abstractions. There are no resource
ID, namespace, or mod-specific branches. The existing Metal opt-in and startup
fallback rules are unchanged; the addon version remains `0.1.0.1`.

## Why OpenGL succeeds and translation failed

Minecraft 26.1.2's bundled core/post shaders use GLSL 330. Its OpenGL backend
passes a pipeline's resource-provider source to the driver; the rendering API
does not require every custom shader to declare 330. Desktop GLSL 150 with
explicit `in`/`out`, `texture`, and std140 uniform blocks is a normal older form.
Minecraft resolves `#moj_import` and raises the effective version when an
import requires a newer version; that does not upgrade an independent fragment
shader without such an import.

The installed SkyBlockPv **1.8.9-26.1** inventory fragment declares 150 and uses
a sampler, an instance-less std140 block with an `ivec2`, explicit varyings and
fragment output, UV arithmetic, and discard. Its vertex source also declares
150 and imports Minecraft's transform/projection blocks. SkyHanni **7.22.0**
and owo **0.12.22** UI resources contain equivalent 150 forms.

shaderc uses glslang's OpenGL SPIR-V rules, which require desktop GLSL 330 or
newer even for preprocessing. The OpenGL 4.5 *target environment* selects SPIR-V
semantics, not the source's GLSL version. Changing the source language to HLSL
or the target to Vulkan would change the language/semantics rather than fix
the input. shaderc explicitly does not support SPIR-V generation for its
OpenGL compatibility-profile target. See the upstream
[environment definitions](https://github.com/google/shaderc/blob/main/libshaderc/include/shaderc/env.h)
and [compiler rules](https://github.com/google/shaderc/blob/main/libshaderc_util/src/compiler.cc).

The translator now explicitly selects GLSL, OpenGL 4.5 semantics and SPIR-V
1.0. Automatic bindings/locations and the existing MSL 2.4, vertex Y/depth
conversion, resource reflection, and fragment-to-vertex varying matching remain.

## Normalization boundary

1. A token scanner reads the actual version directive, ignoring comments and
   recognizing whitespace and CRLF. Desktop 110/120/130/140/150 sources are
   promoted to 330 core; a missing version has desktop GLSL's 110 default.
   Modern desktop core/unspecified versions keep their version. Unsupported
   versions and explicit ES/compatibility profiles fail with a diagnostic.
2. For promoted sources, a collision-free macro alias retains `__VERSION__`
   values and `defined`/`#ifdef` decisions. `#line` restores source locations
   after generated headers. Pre-profile sources that inspect profile macros
   are rejected rather than silently selecting a different branch.
3. shaderc expands macros and conditionals before declaration rewriting. This
   includes pipeline defines already injected by Minecraft. Inactive code and
   comments cannot introduce resources or trigger legacy rewrites.
4. Legacy `attribute` becomes vertex `in`; `varying` becomes vertex `out` or
   fragment `in`. `gl_FragColor` becomes a collision-free fragment output at
   color location zero. Old non-shadow `texture1D/2D/3D/Cube`, projection and
   LOD calls map to the corresponding modern overloads. User-defined legacy
   texture-function declarations are rejected to avoid changing overloads.
5. Only **top-level numeric loose uniforms** move into `EvieMetalDefaults`.
   Their names, arrays, comma declarations, and precision qualifiers survive.
   Uniform blocks (including members explicitly qualified `uniform`) and
   opaque sampler declarations stay intact. Multiple declarations or a
   function on the same line no longer disappear. MSL's reserved identifier
   `sampler` is renamed in SPIR-V, preserving reflected texture bindings.

The old line regex gathered `uniform float DiscardAlpha;` from inside
XaeroLib **1.1.0**'s `DiscardAlphaBlock`, leaving an empty block. That reproduces
the `unexpected RIGHT_BRACE` error even though that shader already declares
330. This is a separate declaration-scope bug, fixed by tracking brace depth.

Translation-cache keys include both the translator and normalization adapter
bytecode, so upgrades cannot reuse translations produced by the old rules.

## Failure ownership

A failed `setPipeline` closes the backend pass immediately, including when
the caller leaves a debug group open or catches the error without closing the
pass. Lazy Metal pipeline-state creation failures also abort the pass. Closing
that old pass later is harmless and cannot close a subsequent pass. Java pass
ownership is reset in `finally` when finishing the native pass. The existing
native encoder merge policy is retained; the next pass reapplies its bindings.

Errors identify the pipeline, shader resource, vertex/fragment stage and
underlying preprocessing, shaderc, SPIRV-Cross, or Apple MSL compiler message.
Cleanup errors are suppressed onto the original failure. This permits error
reporting/cleanup without a secondary "close the existing render pass" error;
it does not introduce a mid-frame OpenGL fallback or silently skip bad shaders.

## Remaining limits

- Fixed-function OpenGL inputs/state (`gl_Vertex`, `gl_Color`, `gl_TexCoord`,
  `gl_ModelViewProjectionMatrix`, `ftransform`, etc.) are not emulated.
  Minecraft's explicit vertex/uniform abstractions do not supply that state.
  Raw OpenGL rendering remains outside this backend.
- GLSL ES and explicit compatibility profiles, legacy shadow sampling,
  `gl_FragData`/multiple or dynamic color outputs, and user-defined overloads
  of legacy texture-function names are not normalized.
- Loose uniform initializers, layout/other leading qualifiers on loose value
  uniforms, and user-defined struct uniforms are not supported by the numeric
  default-block adapter. Arrays with literal/macro-expanded dimensions work;
  dimensions depending on GLSL global constants can fail when moved ahead of
  those constants into the generated header. Blaze3D callers must supply named uniform buffers;
  translation alone does not provide an old OpenGL uniform-setting API.
- Compiler/Metal restrictions on extensions, resource types, interface types,
  and unsupported stages still apply. This is not an arbitrary GLSL emulator.

## Validation

Local validation uses Apple M3 Pro / macOS 27.0.1 / ARM64 Temurin 25.0.3 with
`MTL_DEBUG_LAYER=1`:

- Full project build and native distribution verification pass. All **162**
  main-mod tests and **34** addon tests pass, none skipped. The addon checks all
  81 vanilla core/post shaders and the pinned Sodium terrain variants.
- Synthetic regressions exercise low-version vertex/fragment interfaces,
  uniform blocks, legacy attributes/varyings/sampling/output, macro-generated
  and conditional uniforms, `__VERSION__`, CRLF/comments, explicit `#line`
  errors, arrays/comma declarations, reserved identifiers and unsupported forms.
- A native regression fails each shader stage through Minecraft's public pass
  wrapper, leaves a debug group open, then draws and reads back a correct pixel
  from the next pass. Late closure of the failed pass cannot affect that draw.
- Separate temporary validation reads the installed JARs directly, resolves
  imports with Minecraft 26.1.2's `GlslPreprocessor`, translates them, and asks
  Apple's MSL compiler to compile the results. **44** sources pass: all five
  SkyBlockPv sources (with a representative `COLORS` pipeline define for the
  gradient), 20 SkyHanni UI sources, three owo UI sources, and all 16 XaeroLib
  sources. The two SkyHanni `darken` stages require unsupported fixed-function
  state. SkyHanni/owo binaries here target older Minecraft releases; these are
  shader-source checks against 26.1.2 imports, not mod-binary compatibility claims.
- The actual, unmodified SkyBlockPv inventory sources run through
  `MetalDevice` → `MetalRenderPass` → `MetalPipeline` with Minecraft's explicit
  vertex format, transform/projection/settings buffers, sampler, and texture.
  A draw/readback returns the expected RGBA pixel; a second draw verifies
  transparent-pixel discard. No third-party shader source is copied into the
  repository. This is an isolated rendering check, not live Hypixel gameplay.
- Minecraft's normal launch fixture passes with the pinned Sodium installed:
  Metal initializes, the title-screen frame is rendered/read back, the fixture
  prints `EVIEMOD_METAL_FRAME_OK`, and Minecraft exits successfully.

Reproduce the committed regressions with:

```sh
MTL_DEBUG_LAYER=1 ./gradlew build :metal-addon:verifyDistribution
python3 .github/scripts/verify-metal-artifacts.py
```

CI uses the same committed tests on Linux and macOS (native tests are macOS
only). In-game SkyBlockPv interaction in the complete modpack still needs a
live gameplay check.
