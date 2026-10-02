# Experimental Metal addon

`eviemod-metal-0.1.0.1.jar` is a separately loadable Fabric addon for Minecraft
26.1.2. The main eviemod JAR neither includes nor requires it. The addon also
works without eviemod; Fabric Loader is its only mod dependency.

## Install and enable

Use the **eviemod-metal** artifact from the macOS CI job or a macOS build. Place
the JAR beside eviemod in `mods/`, then add this JVM argument in your launcher:

```text
-Deviemod.metal=true
```

Restart the game. The addon defaults to off, including existing installations.
Remove the argument (or the addon JAR) and restart to return to vanilla OpenGL.
Requires Apple Silicon, an ARM64 Java 25 JVM, and macOS 14 or newer. Running an
x86 JVM through Rosetta is unsupported.

## Scope and failure behavior

The backend implements Blaze3D buffers, textures, samplers, render passes,
pipelines, fences, timer queries, readback, and presentation through native
Metal. Minecraft GLSL is translated through shaderc/SPIRV-Cross to MSL; resource
pack core shaders use the same path. No per-frame OpenGL drawing or CPU image
copy is involved. Metal uses a GLFW window with `GLFW_NO_API`; it does not
require an OpenGL context.

Platform/conflict checks run before loading native code. Disabled/unsupported
launches keep vanilla behavior. Missing libraries, shader-toolchain failures,
or native initialization failures log the reason and let Minecraft close the
Metal window and retry with a fresh OpenGL window. Native preflight completes
before attaching the Cocoa view's layer.

After Metal resources exist, switching those resources to OpenGL mid-frame is
not supported. Pipeline compilation failures throw a clear error; asynchronous
Metal command-buffer errors are surfaced at the frame boundary. Disable the
argument and restart. JVM/native driver crashes cannot be caught by Java.

Initial support is vanilla Blaze3D plus eviemod. Sodium, Iris, VulkanMod,
Metallum, MetalCraft, and MetalRender cause startup fallback. Other mods using
raw OpenGL are unsupported. This is experimental: full gameplay, resource
packs, dimension changes, fullscreen, and resize still need physical Mac validation.
No FPS improvement is promised without measurements on the target hardware.

Memory-lifetime findings and the 0.1.0.1 regression results are in the
[Metal memory audit](metal-memory-audit.md).

## Build and verification

```sh
./gradlew build                                  # main mod + addon Java/tests
./gradlew :metal-addon:verifyDistribution         # requires macOS native
./gradlew :metal-addon:runClient -Pmetal
```

For development, pass the JVM opt-in directly to the Gradle run task using
`-Pmetal`; `./gradlew :metal-addon:runClient -Pmetal`. Xcode Command Line Tools
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
unsupported opt-in addon starts on vanilla OpenGL. A macOS launch fixture also
requires an active Metal backend and a nonblank title-screen screenshot;
that frame includes the 3D panorama, UI textures, and text. GitHub's Mac runner
uses Apple's paravirtual Metal device, rather than a physical Mac's GPU.
These checks are not live gameplay
or a performance benchmark.

Before calling the renderer verified, test on Apple Silicon with Metal API
validation (`MTL_DEBUG_LAYER=1`): menus, a local world (opaque/cutout/translucent
terrain), entities, particles, text/items, Nether/End, screenshots, resource
reload, resize/fullscreen, vsync changes, and exit. Repeat with eviemod and test
startup fallback with the opt-in absent and with Sodium installed.

The implementation is a pinned port of MetalCraft's core; see
[the addon notice](../metal-addon/NOTICE.md) and its GPL license. The sole mixin
prepends a `GpuBackend` to Minecraft's ordered backend candidates in 26.1.2.
The backend uses vanilla's window cleanup/retry loop; its `createDevice` bridge
preserves `BackendCreationException` despite the interface omitting a checked
throws declaration. Keep future API changes at that boundary and in the
backend adapters.
