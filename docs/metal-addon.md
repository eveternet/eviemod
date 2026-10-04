# Experimental Metal addon

`eviemod-metal-0.1.0.1.jar` is a separately loadable Fabric addon for Minecraft
26.1.2. The main eviemod JAR neither includes nor requires it. The addon also
works without eviemod; Fabric Loader is its only mod dependency.

## Install and enable

Use the **eviemod-metal** artifact from the macOS CI job or a macOS build. Place
the JAR in `mods/` and restart the game. Installing the separate addon enables
Metal automatically on supported Macs; no custom JVM argument is required.
Requires Apple Silicon, an ARM64 Java 25 JVM, and macOS 14 or newer. Running an
x86 JVM through Rosetta is unsupported.

To return to vanilla OpenGL, remove the addon JAR or add
`-Deviemod.metal=false` in your launcher and restart. The existing
`-Deviemod.metal=true` argument remains supported but is optional. An existing
explicit `false` override remains disabled after updating; removing a former
`true` argument now leaves Metal enabled while the addon is installed.

## Scope and failure behavior

The backend implements Blaze3D buffers, textures, samplers, render passes,
pipelines, fences, timer queries, readback, and presentation through native
Metal. Minecraft GLSL is translated through shaderc/SPIRV-Cross to MSL; resource
pack core shaders use the same path. No per-frame OpenGL drawing or CPU image
copy is involved. Metal uses a GLFW window with `GLFW_NO_API`; it does not
require an OpenGL context.

Platform/conflict checks run before loading native code. Disabled/unsupported
launches keep vanilla behavior. Missing libraries, shader-toolchain failures,
or native initialization failures without Sodium log the reason and let Minecraft close the
Metal window and retry with a fresh OpenGL window. Native preflight completes
before attaching the Cocoa view's layer.

After Metal resources exist, switching those resources to OpenGL mid-frame is
not supported. Pipeline compilation failures throw a clear error; asynchronous
Metal command-buffer errors are surfaced at the frame boundary. Remove the addon
or set `-Deviemod.metal=false` and restart. JVM/native driver crashes cannot be
caught by Java.

Support includes vanilla Blaze3D and the pinned Sodium **0.9.2+mc26.1.2** terrain
frontend. With Sodium and Metal enabled, unsupported Sodium versions and
Metal initialization failures stop with a clear error instead of silently
retrying OpenGL. The log identifies both the active Metal backend and Sodium's
Metal terrain endpoint. Iris, VulkanMod, Metallum, MetalCraft, and MetalRender
retain their existing startup conflict checks. Legacy GUI state surrounding
Blaze3D submissions is guarded; direct raw OpenGL drawing remains unsupported.
The [GUI compatibility audit](metal-gui-compatibility.md) describes the boundary,
state ownership and remaining direct-LWJGL limits. The compatibility design, physical Mac runtime evidence,
and remaining limits are in [Sodium compatibility](metal-sodium-compat.md).
No FPS improvement is promised without measurements on the target hardware.

Memory-lifetime findings and the 0.1.0.1 regression results are in the
[Metal memory audit](metal-memory-audit.md).
Legacy GLSL normalization, shader failure cleanup, validation and remaining
unsupported forms are described in [shader compatibility](metal-shader-compatibility.md).

## Build and verification

```sh
./gradlew build                                  # main mod + addon Java/tests
./gradlew :metal-addon:verifyDistribution         # requires macOS native
./gradlew :metal-addon:runClient                  # Metal by default on supported Macs
./gradlew :metal-addon:runClient -Pmetal=false    # explicit OpenGL override
```

For development, `runClient` uses the same automatic startup policy as the
installed addon. `-Pmetal=false` passes the explicit JVM disable override;
the legacy `-Pmetal` flag still passes `true`. Xcode Command Line Tools
are required on macOS. Gradle builds an arm64 dylib using the selected JDK's JNI
headers. Linux builds deliberately produce a Java-only addon for compilation
and fallback testing; CI only publishes the macOS addon as a distribution.

CI runs Linux compilation/unit tests and a macOS native distribution build
with Metal API validation enabled.
Shader tests translate the actual 26.1.2 core/post shaders on both platforms
and also compile the resulting MSL using Apple's compiler on macOS. Native
smoke tests verify buffer/texture upload, indexed triangle rendering,
clear/readback, fences, and teardown
without a window. A launch fixture verifies that the mixin applies and the
addon starts on vanilla OpenGL on an unsupported host with no JVM override.
A macOS launch fixture also requires an active Metal backend with no JVM override
and a nonblank title-screen screenshot;
that frame includes the 3D panorama, UI textures, and text. GitHub's Mac runner
uses Apple's paravirtual Metal device, rather than a physical Mac's GPU.
A second macOS launch checks that the explicit disable override still uses OpenGL.
These checks are not live gameplay or a performance benchmark.

Before calling the renderer verified, test on Apple Silicon with Metal API
validation (`MTL_DEBUG_LAYER=1`): menus, a local world (opaque/cutout/translucent
terrain), entities, particles, text/items, Nether/End, screenshots, resource
reload, resize/fullscreen, vsync changes, and exit. Repeat with eviemod and test
startup with `-Deviemod.metal=false`, including Sodium installed.

The implementation is a pinned port of MetalCraft's core; see
[the addon notice](../metal-addon/NOTICE.md) and its GPL license. The backend mixin
prepends a `GpuBackend` to Minecraft's ordered backend candidates in 26.1.2.
The backend uses vanilla's window cleanup/retry loop; its `createDevice` bridge
preserves `BackendCreationException` despite the interface omitting a checked
throws declaration. Keep future API changes at that boundary and in the
backend adapters.
