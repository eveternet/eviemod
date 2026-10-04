# Metal memory audit — 0.1.0.1

Audit of the separate addon at baseline `3c763e6`, following catastrophic process-memory growth reported during gameplay without the FPS mod. The main eviemod artifact is unchanged. At this baseline, Metal required a JVM opt-in.

## Reproduced leak

`Mtl.beginPass` created an autoreleased `MTLRenderPassDescriptor` outside a local autorelease pool. Its attachments retained textures after Java released its handles and after the GPU finished. The pool inside `present` or `fenceWait` could not drain objects created by earlier JNI calls: pools only collect objects allocated inside their own scope.

On an Apple M3 Pro, macOS 27.0.1, ARM64 Java 25, with Metal API validation enabled, the regression creates, clears, waits for, and releases 1024×1024 RGBA textures. After eight warmup passes:

| Workload | Baseline | Fixed |
| --- | ---: | ---: |
| 64 further completed render passes | +268,435,456 bytes (256 MiB) | 0 bytes growth |
| 64 completed 1 MiB buffer copies, both buffers released | 0 bytes growth | 0 bytes growth |

Measurements use `MTLDevice.currentAllocatedSize` after the submitted command completes, not JVM heap size or whole-process RSS. This reproduces one actual resource leak; it does not prove every byte of the reported gameplay growth came from that path.

All JNI entry points now have lexical autorelease pools. Strong context fields and explicitly retained handles preserve objects needed across calls. The asynchronous completion callback has its own pool, as does the existing drawable worker. This follows Apple's [Metal memory-management guidance](https://developer.apple.com/videos/play/wwdc2022/10160/).

## Other findings and fixes

- **Upload staging retained its peak forever.** The allocator never freed retired chunks and only considered the oldest one for reuse. A 4 MiB head followed by repeated 5 MiB uploads could prevent reuse indefinitely. Completed retired chunks are now reclaimed regardless of size. Presentation also trims them, so a loading spike can shrink without another upload. One ordinary 4 MiB current chunk may remain for reuse; completed oversized chunks are released.
- **Uploads could accumulate before submission.** A 64 MiB staging budget now submits and waits for pending work before allocating more. An indivisible upload larger than the budget is permitted (plus at most the ordinary current chunk); it is reclaimed on completion. This is a staging budget, not a cap on textures, live geometry, or total process memory. No staging storage is reused while its latest GPU fence is pending.
- **Presentation ignored a failed wait.** The three-frame limit previously continued submitting after a one-second timeout. It now raises an error after a five-second timeout instead of allowing subsequent frames to accumulate. Slow or wedged GPUs may therefore stop rendering rather than grow memory indefinitely.
- **Native context state survived shutdown.** The regional-clear pipeline cache, profiling sample objects/labels/counter set, accumulated profile results, timing history, and frame-fence ring are now released or reset. The clear cache is owned by the device lifetime, rather than a function-local static that outlives it.
- **Developer profiling could accumulate arbitrary labels.** Pending encoder samples were already limited to 1,024. Stored result keys and label lengths are now also bounded; updates to existing keys continue at the limit. Profiling remains off by default. Short counter readbacks are rejected before accessing their contents.
- **Allocation/error paths lost ownership.** Buffer orphaning now keeps the old handle when replacement allocation fails. Temporary indexed-fan buffers use `finally`; shader functions are released if pipeline construction throws an `Error` as well as a runtime exception. Pipeline/device closure is idempotent and a closed pipeline reports invalid.

## Ownership review

| Area inspected | Lifetime / result |
| --- | --- |
| Buffers and texel views | Explicit retained handles released on close; old storage/view released on orphaning. Queued commands retain resources until GPU completion. A regression checks queued reads across repeated orphaning. |
| Textures and mip views | Each owns a separate retained native handle. Callers must close both. Descriptor retention was the reproduced leak above. |
| Samplers and depth states | Retained on creation and released by their Java owners. Native temporary descriptors now have local pools. |
| Java render passes | Bindings live in the pass object, with no global collection of passes. Native encoder merging retains only the active attachments until encoding ends. |
| Pipeline variants and shader-source maps | Owned by `MetalDevice`; cleared on resource reload and close. Format variants are finite. Arbitrarily many distinct caller-created pipelines can grow the device cache until invalidation; no evidence establishes that as the reported leak. Evicting externally referenced compiled pipelines would require a separate ownership design. |
| Shader translation | shaderc results/options/compiler, SPIRV-Cross context, copied SPIR-V memory, and temporary native libraries have explicit cleanup. The translation cache is on disk, not a growing in-memory map. Its disk usage is not bounded by this patch. |
| Command buffers / completion blocks | Current and last command are strong context references; previous references are replaced on commit. Completion blocks capture profiling resources only until completion. Pools now drain temporary encoder/descriptor objects locally. |
| Presentation / resize | One ready drawable and at most one fetch are held. Stale-size drawables are discarded. Shutdown joins the drawable worker, releases it, and restores the previous view layer. Actual fullscreen/resize gameplay remains a manual check. |
| Fences and timer queries | Java stores scalar fence values; native timing history is fixed at 1,024 entries. No per-query native allocation. |
| Shared fan-index buffer | One buffer sized to the largest requested fan; released on replacement/device close. This is high-water caching, not growth per draw. Temporary indexed fans now release on exceptions too. |
| Startup failures | Bootstrap closes a created device or shuts down partially initialized native state before OpenGL fallback. |
| Native extraction | One loaded dylib per process with a delete-on-exit temporary file; no per-frame extraction. |

## Verification and limits

The final macOS build and distribution check passed; all **14 tests** passed with `MTL_DEBUG_LAYER=1` and none were skipped. The actual Minecraft launch fixture also passed on the M3 Pro: Metal initialized, rendered the title-screen panorama/UI/text, saved a nonblank screenshot through GPU readback, printed `EVIEMOD_METAL_FRAME_OK`, and exited successfully. This was a short title-screen check, not sustained world gameplay.

Regression coverage includes completed attachment release, completed buffer copies, mixed-size staging, the staging submission budget with GPU readback of every copied value, queued reads across buffer orphaning, repeated context/profiling teardown, existing indexed drawing and partial clears, shader translation/compilation, and shader-cache invalidation.

The native build uses the SDK bundled with Xcode explicitly on this machine because the selected compiler and the default Command Line Tools SDK have different versions. This is a local build-environment override, not a source change.

These tests establish resource-lifetime behavior under controlled workloads. They do not establish a flat whole-process memory curve for a long SkyBlock session. Repeat the original gameplay route with the replacement JAR, recording process footprint and Metal allocated bytes after warmup. Include travel/chunk loading, resource reload, screenshots, resize/fullscreen, returning to the title screen, and both vsync settings. JVM heap growth, driver caches, and live terrain/assets must be distinguished from objects that remain retained after their owners close.
