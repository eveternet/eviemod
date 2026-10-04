# Legacy GUI state compatibility

## Root cause and audited versions

Eviemetal uses a `GLFW_NO_API` window. There is intentionally no current OpenGL
context. A legacy `GlStateManager._enableBlend()` reaches `GL11.glEnable`, whose
LWJGL native dispatch can abort the JVM before Java can throw or write a normal
Minecraft crash report. Catching an exception around GUI drawing cannot fix it.

The audit uses Minecraft **26.1.2**, the installed **Devonian 1.31.9** artifact,
and its nested **Talium b81bb9d087c938e0cb2c5465454a8adf057f0b62** JAR. Devonian's
installed JAR SHA-256 is `558b1b96c5ad8c8ce423641f9465d4f6591648ffa9ad32981c46f4bf920e5dee`.
The JAR's
bytecode confirms the source path below, rather than assuming current upstream
has identical APIs. Upstream Talium has since changed Minecraft versions.

* [Talium UIBase.draw](https://github.com/Synnerz/talium/blob/b81bb9d087c938e0cb2c5465454a8adf057f0b62/src/main/kotlin/com/github/synnerz/talium/components/UIBase.kt):
  root drawing enables blend and disables cull before handling input and submitting
  children. Its `finally` disables blend and enables cull. All four calls need guarding.
* [Talium Renderer](https://github.com/Synnerz/talium/blob/b81bb9d087c938e0cb2c5465454a8adf057f0b62/src/main/kotlin/com/github/synnerz/talium/utils/Renderer.kt)
  submits rectangles, gradients, lines and text as Minecraft GUI render state.
  `GradientRectangleState` uses `RenderPipelines.GUI` (or `GUI_INVERT`).
* `ScissorEffect` stores rectangles in Talium's Java scissor stack; each submitted
  GUI element/text state captures its scissor rectangle. It does not issue GL scissor
  calls. Minecraft's GUI renderer later supplies those rectangles to render passes.
* Devonian's config screen forwards key/character input and invokes `background.draw()`
  from `extractRenderState`. The root handles mouse input during that call.
  Its switches leave Talium's radius at zero and use ordinary GUI rectangles.

## Boundary and state ownership

`LegacyGlStateMixin` lives only in the separate Metal addon. Every entry point
checks `MetalBootstrap.isActive()`, just like the existing Sodium compatibility
hooks. Disabled, unsupported-host and failed-startup OpenGL fallback calls execute
the original method unchanged. No dependency on Devonian/Talium is added.

Metal-active interception occurs at method HEAD, before either a GL call or GL
cache mutation. Minecraft 26.1.2 uses deferred GUI submission: the root's legacy
state is already reset before those elements are drawn. Replaying global state
onto Metal would override explicit pipelines with state from the wrong time.
The shim therefore keeps pipeline/pass state authoritative, with no second state
cache, synthetic handles, context, encoder, or resource owner to reset on screen close.

| Legacy operation | Metal-active handling and existing owner |
| --- | --- |
| Blend enable/disable and both separate blend-function entry points | No-op; `MetalPipeline` translates the pipeline's color/alpha blend factors. |
| Depth test, comparison and depth mask | No-op; pipeline depth/stencil state already determines Metal testing/writes. |
| Cull enable/disable, polygon mode and polygon offset | No-op; `MetalPipeline`/`MetalRenderPass` bind explicit cull, wireframe and depth bias. |
| Color write mask | No-op; pipeline color target translates each channel's write bit. |
| Legacy scissor enable/disable/box | No-op; captured GUI scissor rectangles reach the existing `MetalRenderPass` clipping implementation. This does **not** emulate GL global scissor for arbitrary draws. |
| Legacy viewport | No-op; the native render encoder uses the current attachment extent. Custom GL viewport rendering is not emulated. |
| Active texture unit | No-op; Metal binds textures/samplers by shader name and reflected binding index, not a mutable GL slot. |
| Texture/program bind of zero | No-op unbind; per-pass bindings are authoritative. Nonzero GL handles are rejected. |
| GL error query/clear | Return `GL_NO_ERROR` / no-op; no GL work was submitted. Existing Metal error checks remain active. |
| Texture allocation/upload/parameters/query/delete, pixel-store/readback | Unsupported: throw an operation-naming Java `UnsupportedOperationException` before native dispatch or counters/cache changes. Use Blaze3D textures, samplers and command encoders. |
| GL shader/program creation, queries, uniforms; buffers/VAOs/FBOs; raw draw/clear; sync; driver queries; color logic ops | Unsupported through this boundary: throw the same Java diagnostic. No fake success, empty resource or duplicate graphics backend. |

No new opt-in is needed: this corrects the already explicitly enabled Metal path.
The addon and its default-off startup selection, shader compatibility, resource
lifetimes and OpenGL fallback are otherwise unchanged.

## Limits

This is a **GlStateManager** boundary, not interception of LWJGL itself. Mods
calling `org.lwjgl.opengl.GL*`/ARB APIs directly can still abort the JVM. Supporting
arbitrary raw GL drawing/resources would require a much larger renderer and is
outside this fix. Unsupported calls through GlStateManager now produce a Java
error; a caller that does not catch it may still cause a normal Minecraft crash.

Talium's old `Shader`, uniform and rounded-rectangle helpers call LWJGL directly.
The audited Devonian settings controls do not use them (switch radius is zero).
Another Talium consumer choosing rounded switches would need separate work.
No compatibility with different Talium/Minecraft versions is implied.

## Regression checks

`LegacyGlBoundaryTest` compares the pinned Minecraft class's entire public static
method inventory, including descriptors/overloads, to the mixin's cancellable HEAD
guards. An added or changed wrapper fails the test and requires a new audit.

The existing launch fixture runs `LegacyGuiStateProbe` through actual applied mixins:

* Metal: assert no current GL context, run 1,000 complete state cycles including
  Talium's opening/finally calls, require every unsupported wrapper to throw in
  Java, and compare all GL caches/counters before/after.
* OpenGL fallback: assert a current GL context, query real GL blend/cull/depth/blend
  factor state, and allocate/bind/delete a real GL texture to confirm forwarding.
* The existing Metal frame render/readback then checks the renderer still produces
  a nonblank Minecraft image. Linux and macOS CI already execute these launch fixtures.

## Local validation, 2026-10-03

On the physical Apple M3 Pro with ARM64 Java 25:

* `./gradlew build :metal-addon:verifyDistribution :metal-addon:smokeTestClasses -PmetalSmokeTest`
  passed. Main tests: **162**, addon tests: **35**, no failures/errors/skips.
  The added inventory regression covers all **78** public static GL wrappers.
  `python3 .github/scripts/verify-metal-artifacts.py` passed, confirming the main
  artifact neither contains nor requires the addon.
* `MTL_DEBUG_LAYER=1 ./gradlew :metal-addon:runClient -PmetalSmokeTest -Pmetal -PexpectMetal`
  passed with the pinned Sodium present. The transformed Metal state probe passed
  1,000 cycles and every unsupported wrapper test with **no current GL context**;
  all legacy caches/counters stayed unchanged. Title rendering/readback passed.
* `./gradlew :metal-addon:runClient -PmetalSmokeTest` passed on real OpenGL with
  Metal disabled. The probe verified native state changes and texture allocation,
  binding and deletion. The production mixin's inactive branch stayed transparent.
* The **packaged addon JAR**, installed Devonian JAR, its nested Talium, Fabric API,
  Fabric Language Kotlin and Hypixel Mod API were loaded in separate fresh game
  directories. No normal modpack settings or worlds were changed. The optional
  screen fixture selects `com.github.synnerz.devonian.config.ui.talium.ConfigGui`
  with `-Deviemod.metal.guiSmokeScreen=...` and opens/closes it **20** times before
  taking the ordinary frame screenshot. Both Metal and OpenGL runs exited **0**.
  Settings text, category/subcategory tabs, switches and clipping rendered correctly.
  The 1708x960 base GUI captures differed in only **27 of 1,639,680 pixels**.
* A separate local fixture compiled against the actual installed JARs exercised
  Talium's normal event propagation and Devonian's Screen key/character handlers:
  toggle/restore a switch, select Dungeons/Global, type a search, scroll results,
  select Dungeon Colors, open/render/close a color picker. All assertions passed
  with Metal validation enabled. Input tests synthesize the same component events;
  they do not substitute for testing physical mouse/keyboard input in the modpack.
  `Mtl.allocatedBytes()` after each of the 20 screen closes was **132,431,872**
  bytes in this run, with no accumulation. This bounds the tested screen lifecycle,
  not all modpack/game resource lifetimes.

The reusable probes are in the existing development smoke-test source set and do
not ship in the production JAR. The mod-specific local event fixture was temporary,
outside the addon; production compatibility contains no Devonian/Talium names or
dependency. Live Hypixel gameplay and the full normal modpack were not tested here.

The [PR CI run](https://github.com/eveternet/eviemod/actions/runs/37159130795)
passed Linux fallback and macOS Metal launch probes. Its `metal-launch-evidence`
artifact contains the Metal launch log, frame screenshot and test results.
The local Devonian-specific runs above are separate from those CI probes.
Game data/output is ignored and excluded from commits; probe failures fail the
existing CI jobs.

## Manual modpack checks

With the Metal flag enabled, open `/devonian`, select categories, search/type,
scroll long lists, toggle a switch and restore it, drag sliders, open/close a color
picker, and close/reopen the GUI repeatedly. Check GUI scale, resize/fullscreen,
tooltips, clipping, return to gameplay, and SkyHanni/SBPV settings/inventory rendering.
Repeat `/devonian` with the Metal flag removed to check vanilla OpenGL behavior.
