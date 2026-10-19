# 26.2 CPU path review — 2026-10-19

## Scope and baseline

Authoritative local project: `/Users/eveternet/Documents/ChatGPT/skyshitter`, remote
`eveternet/eviemod`. Its clean active checkout was `codex/quick-navigation` and
was not modified. Local and GitHub PR 17 both identified
`codex/eviemetal-dual-minecraft` at `d9ced1396118c5e334c904979737e018b9dbb823`.
PR 17 was open, no longer draft. Work used an independent local clone of that
branch. PR 21, the combined feature branches, and Simplechat were excluded.

No desktop automation, game launch, focus change, fullscreen change, existing
world modification, or Vulkan performance comparison was performed. The only
GPU work was windowless tests and setup/upload microbenchmarks on Apple M3 Pro,
Java 25.0.3. The initial root build, both native distributions, and 444 tests
passed with `MTL_DEBUG_LAYER=1`.

## Three focused loops

1. Inspected the actual cached Minecraft 26.2 OpenGL sources, the 26.2 Metal
   adapter, pinned Sodium integration, native submission/synchronisation, and
   existing diagnostics. Identified unnecessary binding-plan invalidation and
   overwritten-span copying. These are demonstrated local CPU costs, not an
   established explanation for the user's gameplay FPS. The older
   `SodiumMetal.performanceCounters()` describes the 26.1.2 adapter, not 26.2.
2. Retained resolved bindings when a uniform's buffer identity, offset and
   length are unchanged. Identical texture/view/sampler bindings avoid a fresh
   binding record and invalidation. Changes to an unrelated uniform no longer
   discard a texel view's resolution. Every draw still validates resources and
   detects native storage generations; offset/length changes and removal still
   invalidate. The pixel test observes 101 draws resolve the unchanged texel
   slice once, including alternating ordinary uniform bindings.
3. Partial orphan writes preserve only the prefix and suffix outside the new
   data. Storage replacement, GPU ownership and fences are unchanged. Reviewed
   aliases, queued old-storage reads, generation changes, pipeline/timestamp
   replay, texture replacement/removal and closed resources. Added native
   regression coverage and repeated baseline/candidate measurements. No
   synchronisation removal, buffer pool, shader rewrite or renderer redesign.

Production changes are confined to the 26.2 Java adapter. Shared native code,
26.1.2 renderer, shader compilation, terrain batching and dependencies are
unchanged. Versions remain unchanged; these are local test builds, not releases.

## Measurements and limits

`PerformanceProbe` is an explicit, windowless CPU microbenchmark. Its setup case
uses four uniform buffers and one texel buffer, repeats identical setters, and
invokes the real private pass `setup()` through a MethodHandle. It deliberately
does **not** issue draws. This isolates binding resolution and its allocation
cost, but omits submission, frame construction, world simulation, visibility,
presentation, fill rate and realistic binding distributions. Texture-record
allocation savings are not separately timed.

The upload case repeatedly orphans a 256 KiB destination at offset 2 KiB with
three source lengths. It includes native buffer allocation and CPU copying but
no queued GPU workload. It cannot predict chunk-rebuild frame times.

Three fresh baseline JVMs and three candidate JVMs were sampled, interleaving
baseline/candidate after the first exploratory baseline. Per-process medians
exclude the first four of ten setup rounds (200,000 operations/round) and the
first three of eight upload rounds (1,000 operations/round). The table shows
ranges of those three medians, not confidence intervals. Validation and GPU
profiling were disabled for timing; no claim is made that the entire machine
was otherwise idle.

| CPU microbenchmark | Baseline | Candidate |
| --- | ---: | ---: |
| Repeated binding setup | 94.35–97.19 ns/op | 34.80–35.59 ns/op |
| Setup thread allocation | 72 bytes/op | 0 bytes/op |
| 4 KiB orphan update | 22.58–24.50 us/op | 22.93–23.70 us/op |
| 128 KiB orphan update | 25.53–26.02 us/op | 22.87–24.91 us/op |
| 252 KiB orphan update | 27.88–29.09 us/op | 21.73–22.48 us/op |

The setup savings are consistent in this narrow workload. Small partial uploads
have no consistent timing improvement. The deterministic copy count for a
partial update of length N into size S changes from S+N bytes to S bytes;
full replacements already copied only S bytes. Large partial updates benefit
in these samples. **No FPS, frame-time, or OpenGL-versus-Metal speedup was
measured.** These small changes are not evidence of a major FPS improvement.

Reproduce the candidate probe, separately from validation:

```sh
./gradlew :metal-addon-26.2:benchmarkCpuPaths
```

Do not set `MTL_DEBUG_LAYER=1` or `-Deviemod-metal.gpuProfile=true` for throughput
measurement. To reproduce the baseline with the same harness, use the probe and
build task on `d9ced13` without the production changes. Baseline timing here
used saved baseline compiled classes and identical dependency/native classpaths.
The probe is test-only and is absent from distributable jars.

## Verification

- Root `build verifyMetalDistributions` passes for 26.1.2 and 26.2.
- 447 tests: 172 main-mod tests per target, 48 retained addon tests, 55 26.2
  addon tests; zero failures, errors or skips. Changed addon tests ran again
  after each change; unchanged tasks can be up-to-date after the fresh baseline.
- New tests check pixel colours through repeated/equal bindings, ordinary
  uniform changes, texel offset/length changes, orphaned storage, closed
  resources, texture replacement/removal, aliased upload input and queued reads.
- Existing tests cover timestamp interruptions, sliced instancing, reversed
  depth/blending, pipeline failures/recovery, shader/Sodium translation,
  transient retirement, upload-ring lifetime and memory ownership.
- Artifact separation verification passes. Both jars contain the arm64 native
  bridge. No client title/world/SUMR or full-modpack graphical smoke was rerun
  locally, because that would create windows on the user's desktop.

## Next useful measurements

Before launching any live benchmark, agree a dedicated test instance and a
copied world (never modify an existing active session). Run 26.2 OpenGL and
26.2 Metal sequentially, with the same exact Sodium version, other mods/resource
packs, world snapshot, dimension, camera coordinates/orientation or recorded
route, render/simulation distance, quality settings, actual framebuffer
resolution, fullscreen mode, FPS cap and vsync. Capture those inputs with the
result. Do not use Vulkan as a baseline.

Warm up each workload for at least two minutes and until chunk builds and
shader compilation settle; then collect at least three alternating 60-second
samples per backend, separating stationary terrain, movement/rebuild and GUI
workloads. Report median, p95/p99 frame time and low-FPS distribution, not just
the title-screen/F3 FPS number. Match thermal/power conditions. Diagnostic
captures should be separate from throughput samples.

Prioritised leads for that run:

1. CPU versus GPU/presentation limit: use a JFR CPU/allocation capture and the
   existing `Mtl.takeGpuSeconds()` / `-Deviemod-metal.gpuProfile=true` per-pass
   diagnostics at controlled boundaries. Completed GPU timing is asynchronous;
   do not insert a fence wait every measured frame to make it synchronous.
2. Sodium batch submission: `multiDrawIndexed` already crosses JNI once per
   batch, but encodes one Metal indexed draw per non-empty command. Measure
   batch count, command count and CPU encoding time before considering changes.
3. Upload stalls: `MetalBuffer.map` waits for pending GPU writes, while small
   buffers still allocate on orphan writes. The current patch reduces copies,
   not allocations or waits. Trace which buffers/updates actually dominate.
4. Timestamp encoder splits: 26.2's TimerQuery issues frame-boundary timestamps;
   Tracy adds scoped queries when enabled. The Metal bridge ends/restarts
   encoders to sample supported hardware counters. Measure splits and avoid
   using instrumented runs as raw throughput comparisons.
5. Presentation and retirement: the surface bounds frames in flight and may
   skip presentation when an immediate-mode drawable is unavailable. Transient
   rotation can wait after three pending rotations. Count real presented frames
   and wait time; do not infer visible throughput from loop iterations alone.

These are leads from code inspection, not diagnosed gameplay bottlenecks.
