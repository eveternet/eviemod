# Eviemetal performance roadmap: comparison with mcopt

Research date: 2026-10-05. This is a source audit and benchmark design, not a measured performance comparison or an implementation change.

## Executive summary

Eviemetal already has native Metal rendering, functional Sodium terrain submission, compact Sodium vertices and CPU optimizations, native encoder merging, pipeline and shader-translation caches, ordinary resource-binding deduplication, shared buffers, ordered uploads, bounded staging memory and bounded GPU work in flight. Sodium integration and repairs for historical leaks are **not** proposed as new work.

The strongest initial opportunities are within those existing paths:

1. **Reduce small-buffer allocation and copying.** Eligible writes currently allocate new Metal storage even when the buffer is idle; partial updates copy its entire previous contents. mcopt demonstrates usage-aware idle writes and completion-safe spare storage. Eviemetal needs complete GPU-use and storage-generation tracking before adopting that idea.
2. **Make Sodium batch setup persistent.** Each region batch rebuilds a structural PSO key with a stream, attribute records and a list. Terrain also rebinds unchanged globals, textures and the vertex arena. Canonicalize immutable layouts, pre-resolve binding plans and deduplicate using native handles, offsets, content generations and encoder generations.
3. **Investigate remaining clear/store transitions.** Both renderers already merge native encoders. mcopt additionally defers full clears, discards proven-dead attachment contents and sometimes folds a depth clear into a continuing encoder. These can reduce target traffic, subject to correct sampling/copy/view invalidation.
4. **Experiment with eligible adjacent quad-range merging.** Eviemetal's multi-draw already crosses JNI once per batch, but still issues one Metal draw per range. mcopt's separate opt-in merger offers a smaller, more reversible experiment than GPU occlusion.
5. **Keep GPU-driven terrain as a later project.** mcopt's pulled opaque/cutout terrain and same-frame hi-Z can reduce submission and vertex work, but add compute, a pass split, memory and invalidation complexity. Its automatic selector exists because the path can lose at high resolution. Preserve sorted translucent terrain and generic/modified shaders on the current path.

No FPS ranking is supported. Eviemetal targets Minecraft 26.1.2/Sodium 0.9.2; the inspected mcopt release targets Minecraft 26.3/Sodium 0.9.3-alpha.1 and a newer rendering API. mcopt's shipping profile also changes chunk meshing, clone caching, render-list collection, startup and thread QoS. Published FPS cannot isolate these effects or predict performance in a SkyBlock modpack.

## Scope, revisions and evidence

| Input | Revision / configuration inspected |
| --- | --- |
| Eviemod authoritative source | `100806d45011cbe922a8f2091cea703311c4e9cd`, release 1.3.0.0 |
| Eviemetal addon | Version 0.1.0.1; Minecraft 26.1.2; optional Sodium `0.9.2+mc26.1.2`; Java 25; macOS 14+ |
| mcopt | Tag `v0.2.0-alpha.1`, commit `7fdeeea5845f22081308c17b4aca17eae2b6930e`; release documentation requires macOS 26+ |
| mcopt dependencies | Minecraft 26.3; Sodium `0.9.3-alpha.1+mc26.3`; Java 25 |
| Eviemetal's Sodium source | `eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413`, tag `mc26.1.2-0.9.2` |
| mcopt's Sodium source | `44b89f42dec873aa7c799224d7a10359b393a487`, tag `mc26.3-0.9.3-alpha.1` |
| Research environment | Linux x86_64; no Apple GPU/macOS, running Minecraft or matched frame traces |

These revisions are authoritative for this report. Unpublished changes on another machine cannot be inspected here. Only this documentation file is proposed; no renderer, benchmark or instrumentation code was changed. No game/GPU benchmark was run. Version requirements are grounded in build metadata and release documentation. [E-build], [E-properties], [M-build], [M-properties], [M-release]

Evidence terminology:

- **Observed in source:** establishes operations and ordering, not their share of frame time.
- **Architectural inference:** predicts a bottleneck or effect that profiling must confirm.
- **Upstream-reported measurement:** a claim in mcopt comments/README or existing Eviemetal documents; not reproduced here and not a matched comparison.
- **Unknown:** available code/data cannot settle the question; the finding names the needed measurement.

Impact ratings describe plausible affected workloads, not promised whole-game speedups. High confidence that an operation is unnecessary does not imply a large FPS gain. All source references below pin commits; Java/native symbols identify the relevant implementation within those files.

## Architecture comparison

### Eviemetal frame

`MetalBootstrap` selects Metal before GLFW window creation and activates it after preflight/attachment. Platform, mod inventory and Sodium version decisions occur at startup. `MetalDevice` supplies one Java `MetalCommandEncoder` and owns ordinary pipeline/source caches. Blaze3D passes become `MetalRenderPass` objects. The native command buffer is lazy; a logical Java pass close leaves the native render encoder available to merge with another pass on equivalent attachment views without clears. Blits, target changes, clears and commits can end that encoder. [E-bootstrap], [E-device], [E-pass], [E-native-pass]

Ordinary draws accumulate named uniforms/samplers. `setup()` resolves reflected resources, deduplicates native bindings, selects the attachment-format PSO and calls JNI. Fresh shared buffers can receive initial data directly. Later writes use eligible orphaning or a CPU copy into `UploadRing` followed by an ordered Metal blit. Private textures receive packed shared staging data through blits. [E-pass], [E-buffer], [E-upload], [E-ring], [E-native-resources]

Sodium retains its CPU meshing, visibility, render lists, compact 20-byte vertices, directional face selection, sorting, arena policies and cached batches. Its older GL-oriented GPU boundary is adapted by `SodiumRenderDevice`, synthetic integer object identities, program/uniform adapters and `SodiumMetal`. Actual Sodium GLSL is translated. For each region batch Eviemetal resolves arena buffers/layout, looks up a structural PSO, binds resources and passes element counts, byte index offsets and signed base vertices in **one JNI call**. Native code loops over ordinary Metal indexed draws. [E-sodium], [E-sodium-device], [S-old-draw], [E-native-draw]

Terrain opens its logical pass lazily. Mapping/copies/uploads can suspend logical ownership; an actual blit closes the native encoder. A CPU mapping or small orphan does not necessarily force a native split. Sodium's fenced persistent staging ring is retained. Section timestamps deliberately use ordered writes rather than permanent unfenced mutation. [E-sodium], [E-times], [E-sodium-device], [S-old-stage], [S-old-times]

Presentation samples the finished offscreen target into an available drawable, commits, records a shared-event fence and throttles GPU backlog. With VSync off it asynchronously prefetches drawables and can skip presenting a rendered frame when none is ready. `Mtl.fence()` returns the next commit's scalar value; it does **not** submit a command buffer. A wait on an unsubmitted fence can submit first. [E-native-frame]

### mcopt frame

mcopt implements Minecraft 26.3's `renderpearl` backend/device/encoder/surface interfaces. Newer Sodium submits through Minecraft render passes: `GLDrawBatch.draw()` calls `RenderPass.multiDrawIndexed()`, and `GLDrawContext` holds the pass. The class name does not imply an actual OpenGL context. This explicit boundary avoids much of Eviemetal's older GL-program/handle adaptation; the advantage partly belongs to upstream API evolution. [M-device], [M-pass], [S-new-draw], [S-new-context], [S-new-renderer]

`MetalRenderPass` uses numbered pending/bound uniform arrays and a dirty upper bound. `MetalEncoder` tracks buffer use by submit index, rotates transient shared blocks after completion, directly writes idle small buffers and can rename eligible busy full replacements using completed spares. Native calls use cached FFM downcalls, with many setters/draws marked critical. Ordinary multi-draw is also a native draw loop, not one hardware multi-draw. [M-pass], [M-encoder], [M-buffer], [M-transient], [M-ffi], [M-native-draw]

Full clears can remain pending until attachment use or a read/copy forces materialization. Native encoders merge across frontend passes and choose stores at their actual end; a pending clear can make old contents dead. A matching-target depth clear can be a depth-writing triangle within the continuing encoder. [M-encoder], [M-native-pass], [M-native-clear]

By default, exact named Sodium terrain pipelines use bundled MSL. The additional `MetalTerrain` path has `occ=auto`: record eligible opaque/cutout ranges, list previously visible quads in a pre command buffer, draw that list in the main render pass with a pulled shader and indirect instancing, split the main pass, build current-frame hi-Z, compact remaining eligible quads in order and draw the resulting list. Translucent/OIT or unmatched shaders remain on other routes. `MetalOccPick` periodically compares split/plain frame timings at the current target size. [M-pipeline], [M-terrain], [M-occ], [M-native-terrain]

The surface acquires a drawable late, optionally gated by display-refresh pacing, and samples the offscreen image into it. Submission may include a pre command buffer plus the main buffer. Java retains resource/completion actions and retires the queue to its limit. Frame generation, render scaling/MetalFX, LOD and recording are separate features that must be disabled for equal-quality comparisons. [M-surface], [M-encoder], [M-native-submit], [M-fx], [M-framegen]

### Mechanisms Eviemetal already implements

| Mechanism | Eviemetal | mcopt | Interpretation |
| --- | --- | --- | --- |
| CPU terrain frontend | Sodium retained | Sodium retained plus optional patches | Compare versions/patches, not presence of Sodium |
| Plain multi-draw | One JNI call then native loop | One FFM call then native loop | Neither plain route collapses the batch to one Metal draw |
| Shared buffers | CPU mappings plus ordered updates | CPU mappings plus usage-aware updates | Shared storage itself is not missing |
| Ordinary binding dedup | Per-stage handle/offset arrays | Numbered pending/bound slots | Eviemetal already skips unchanged native bindings |
| Encoder merging | Equivalent views, no clear | Equivalent targets plus some clear handling | Focus on remaining boundaries |
| Translation cache | Disk GLSL-to-MSL cache enabled at startup | Optional SPIR-V-to-MSL cache | Shader caching is not unique to mcopt |
| Flight throttle | Three fence slots; waits after advancing | Queue default two after submitting | At most two ordinary unfinished submitted frames remain after either throttle |
| Presentation | Fullscreen sampling pass | Fullscreen sampling pass | No evidence presentation-format imitation alone helps |

Eviemetal stores frame N's fence, advances its three-slot ring, then waits on N-2. Changing the constant to “two like mcopt” would reduce overlap, not reproduce mcopt's queue semantics. Count temporary backlog and extra fence-triggered submits separately. [E-native-frame], [M-encoder]

## Performance differences

### D1. Small updates allocate even when existing storage is idle

**Eviemetal:** `MetalBuffer.tryOrphanWrite()` allocates new storage on each eligible write when total buffer size is at most 256 KiB and no GPU write remains incomplete. Partial updates copy the full old buffer, then the patch; old storage/view handles are released. GPU reads are not tracked for an idle-write alternative. Renaming preserves already queued readers, but costs allocation/copy. [E-buffer], [E-upload]

**mcopt:** `MetalEncoder.use()/idle()` track last GPU use. Small writes can copy directly to idle storage; eligible busy full replacements call `MetalBuffer.rename()`, recycling spares only after completion. Partial busy updates still stage/blit; mappings and terrain arenas prohibit renaming. Its 256 KiB threshold tests transfer size, not total size. [M-buffer], [M-encoder]

**Consequence/confidence:** allocation/API overhead and CPU memory bandwidth; impact **medium**, potentially **high** for frequent partial writes near the limit, **low** in static scenes. Source confidence **high**, total frame impact **medium**. Native allocation is not Java allocation, so GC alone cannot explain it.

**Adaptation:** first track every GPU reference, including Sodium bindings, copies, texel views and future indirect uses. Direct mutation requires all relevant use to be completed. Bound spare bytes, preserve partial contents and close-time release, and make ordinary identity-based vertex caches observe storage generations. Never swap backing storage underneath an outstanding mapping.

**Measure:** patched versus copied bytes, orphan/native-allocation counts, allocation/copy CPU time and view recreation, correlated with frame tails. Separate full/partial, mapped and GPU-written cases.

### D2. Sodium rebuilds immutable layout objects per region batch

**Eviemetal:** each nonempty `SodiumMetal.multiDraw()` constructs a `PsoKey` using `Arrays.stream(attributes).map(... new Attribute ...).toList()`, then hashes the structural list for a cache lookup. Some integer vertex-format cases create small lookup arrays too. Cache hits still reconstruct the key. Structural keys correctly avoid one PSO per region tessellation. [E-sodium]

**mcopt:** receives a compiled `MetalPipeline` and binds its stored PSO; normal Sodium batches do not build a vertex-layout key at draw time. This partly reflects the newer API. [M-pass], [M-pipeline], [S-new-renderer]

**Consequence/confidence:** CPU setup and allocation/GC proportional to batches, not indices. Impact **medium** for CPU-bound large-distance terrain, otherwise **low**. Source confidence **high**, material impact **medium**; measure JIT escape elimination.

**Adaptation:** canonicalize immutable structural layouts at tessellation construction/version change and reuse attachment/program-specific resolved variants. Preserve structural equality and program-lifetime invalidation; an identity-only cache risks recreating a PSO per region.

**Measure:** JFR allocation stacks for streams/lists/attributes/keys, batches/frame, lookup time and PSO hit/miss/count across world reloads.

### D3. Terrain bypasses ordinary binding deduplication

**Eviemetal:** ordinary `MetalRenderPass` caches resources, but the separate Sodium path sets the vertex arena per batch and walks both stage maps. `bindStage()` invokes `setBuffer`, `setTexture` and `setBytes` without equivalent slot deduplication. Globals/atlas/lightmap/time views often remain unchanged; region uniforms change. [E-pass], [E-sodium]

**mcopt:** pending/bound slots and `dirtyUpTo` skip unchanged ordinary/Sodium uniforms. Push constants still copy region data; native helpers bind uniforms to both stages, so mcopt is not universally minimal. [M-pass], [M-native-draw]

**Consequence/confidence:** JNI/driver submission overhead and repeated small copies. Impact **medium** for many region batches, **low** for few. Confidence **high** in redundancy, **medium** in whole-frame relevance.

**Adaptation:** pre-resolve numeric terrain binding plans; cache by handle/offset and mutable-content generation. Pointer equality cannot deduplicate `setBytes`. Invalidate for native encoder changes, intervening ordinary rendering, program/target changes, storage renames and view refresh. Logical pass merging does not establish ownership of inherited state.

**Measure:** attempted/actual/skipped bindings by slot/stage, region bytes copied, JNI calls/batch and render-thread submission time. Validate scissor and all state after pass resumption.

### D4. Ordinary setup resolves resources at the name/reflection layer per draw

**Eviemetal:** `MetalRenderPass.setup()` traverses reflected maps and named bindings even when native setters are deduplicated. Texture binding creates a `Binding` record and uniform binding creates a slice. Lifetime checks are still necessary. [E-pass]

**mcopt:** numbered setters mark pending slots, and `bindUniforms()` returns immediately when nothing is dirty. It still allocates arrays, per-pass view maps and optional restorers; it is not allocation-free. [M-pass]

**Consequence/confidence:** CPU/GC overhead in GUI/item/entity-heavy frames; impact **low to medium**, source confidence **high**, total share **uncertain**. This differs from D3's actual native-call redundancy.

**Adaptation:** keep the public named API, resolve names once into per-pipeline plans and track dirty slots. Include lifetime/storage generations; do not remove safety checks merely to save branches. Pool pass structures only when profiles justify it.

**Measure:** setup time/allocations in populated inventories, SkyHanni overlays and repeated Devonian settings with matched UI contents/scales.

### D5. Deferred clears and proven-dead stores can avoid native boundaries

**Eviemetal:** full clear methods immediately begin/end a pass; native merging rejects a clear and attachments initially use `Store`. Depth-only clears can change attachment combinations and force a later load. Ordinary no-clear merging already works; regional clears use a separate shader path. [E-upload], [E-native-pass], [E-native-clear]

**mcopt:** pending full clears materialize on attachment use or before reads/copies; old contents can be marked dead. `endRender()` chooses `DontCare` only for dead/memoryless attachments. A matching-target depth clear can be a triangle within the existing encoder. [M-encoder], [M-native-pass], [M-native-clear]

**Consequence/confidence:** fewer encoder transitions and stores/reloads on tile GPUs, offset by depth-clear geometry. Impact **medium**, potentially **high** at high resolution with frequent stores. Mechanism confidence **high**, workload relevance **medium**.

**Adaptation:** start with explicit full clears and proven absence of surviving reads. Track subresources/views, sampling, copies, readback and presentation, including Sodium's direct bindings. Preserve partial clears. Folded clears must invalidate and restore pipeline/depth/buffer/cull/bias/scissor state.

**Measure:** logical passes versus real encoders, merge success, split reasons, clear/store counts, attachment traffic and GPU time at native resolution. Draw counts alone do not measure this benefit.

### D6. Bounded upload storage releases more completed chunks

**Eviemetal:** `UploadRing` has 4 MiB chunks, 16-byte alignment and a 64 MiB budget. Its completed current chunk resets; completed retired chunks are released rather than pooled. Repeated bursts can allocate/release again. Budget pressure can submit/wait with a five-second bound and trim. Each reservation's fence is a scalar query, not a commit. [E-ring], [E-native-frame]

**mcopt:** the newer transient API uses 1 MiB shared blocks for staging/device slices, rotates per submit and returns blocks through completion actions. Renaming has separate spares. No equivalent 64 MiB cap was established in this allocator. [M-transient], [M-buffer], [M-encoder]

**Consequence/confidence:** allocation bursts versus retained memory and synchronization pressure. Impact **medium** for loading/teleport tails, **low** steady-state; confidence **medium**.

**Adaptation:** retain a small bounded completed-chunk free list, trim oversized/idle chunks and preserve budget/timeout protection. Direct transient GPU slices cannot replace ordered persistent-buffer updates indiscriminately. Cache submission fence stamps only with correct commit-generation invalidation.

**Measure:** upload/reserved/retained bytes, allocations, trims, waits/duration, high-water demand and fence-triggered native splits.

### D7. Four-byte section timestamps can trigger much larger work

**Eviemetal:** `SectionTimeUploadsMixin` suppresses permanent timestamp mapping. Pinned Sodium then issues ordered writes for invalidation and four-byte timestamps. A small time buffer can be orphaned/copied in full per update; a large one stages/blits. Its signed texel view must track backing storage. This is an existing ordering safeguard. [E-times], [E-buffer], [E-sodium], [S-old-times]

**mcopt:** newer Sodium exposes time data as an ordinary pass resource, handled by the usage-aware update route and pending bindings rather than Eviemetal's old texel-view/program adapter. This does not prove unfenced direct mutation is safe. [S-new-renderer], [M-encoder], [M-pass]

**Consequence/confidence:** allocation/copy or blit-split bursts during rebuilds. Impact **medium**, possibly **high** in teleport tails; source confidence **high**, frequency/total cost **uncertain**.

**Adaptation:** batch changes between known observation/order boundaries, or use fenced buffer versions. Preserve region invalidation, intermediate observability and signed `-1`. Keep mapping suppression until actual reuse is fenced.

**Measure:** updates/frame, copied/uploaded bytes per four-byte patch, view allocations and native splits during full rebuilds.

### D8. Texture preparation performs row/channel work in Java

**Eviemetal:** matching-format uploads copy row by row; mismatched NativeImage components convert per pixel/channel in Java before a private-texture blit. Even a contiguous matching rectangle performs one copy per row. [E-upload], [E-native-resources]

**mcopt:** receives already formatted source data through the newer API, copies supplied bytes through transient memory and blits. Buffer-slice texture copies can preserve supplied stride. That method does not implement Eviemetal's older image conversion contract. [M-encoder], [M-transient], [M-native-resources]

**Consequence/confidence:** CPU packing/copy overhead, especially dynamic GUI textures/animations/reloads. Impact **low**, **medium** for frequent large uploads; source confidence **high**, frame relevance **uncertain**.

**Adaptation:** copy contiguous matching rectangles once while retaining row-stride/subrectangle/conversion correctness. Consider native/vector packing or direct staging sources only after measuring and tracking source lifetime/order.

**Measure:** frequency, bytes, component mismatches, packing time and duplicate copies in the real pack, resource reloads and atlas animations.

### D9. Pipeline first use differs; shader caching is already present

**Eviemetal:** translated GLSL/MSL results are disk-cached with shader/compiler-adapter identity, enabled at startup. Format-specific native PSOs are cached but can be created synchronously at first use; native libraries/functions and PSO creation are separate costs. [E-shader-cache], [E-translator], [E-pipeline], [E-bootstrap], [E-native-shaders]

**mcopt:** worker-side pipeline compilation eagerly builds depth/no-depth variants and eligible pulled variants. Its MSL disk cache is optional and defaults off; exact bundled terrain MSL bypasses translation. Neither inspected renderer explicitly implements a Metal binary archive. [M-pipeline], [M-native-resources]

**Consequence/confidence:** cold/world/GUI first-use hitches, not necessarily warm throughput. Impact **medium** on 1% lows/cold transitions, **low** after all variants are warm. Mechanism confidence **high**, observed stall magnitude **unknown**.

**Adaptation:** profile translation/library/PSO separately, prewarm a measured small set of actual formats and deduplicate in-flight compile requests. Asynchronous compilation requires correct device/thread ownership and no frame-blocking joins. Keep generic shader compatibility. Experiment with driver-level archives only after proving repeated native compilation dominates.

**Measure:** cache hits/misses and durations for each stage, first-use frame tags, queue/wait time and retained pipeline counts over reloads.

### D10. Lean bundled terrain MSL trades precision and interpolation semantics

**Eviemetal:** translates the actual Sodium shader, including supported compatibility transformations. Terrain color/fog interfaces follow generated shaders. [E-sodium], [E-translator], [E-glsl]

**mcopt:** exact named solid/cutout/translucent pipelines can use bundled MSL with half color/fog varyings and a leaner interface. Vertex-side folding of fade/fog changes interpolation of nonlinear operations; this is not necessarily bitwise-equivalent to generic translation. The reduced varying-size claim in comments is upstream rationale, not a measured Eviemetal gain. [M-pipeline], [M-terrain-vs], [M-terrain-fs]

**Consequence/confidence:** GPU interpolation/register/bandwidth opportunity; impact **medium** when terrain shading dominates, otherwise **low**. Confidence **medium** in potential benefit, **high** that blindly overriding by name risks modified shaders.

**Adaptation:** test interface precision independently under full source/define/layout fingerprints. Validate fog boundaries, chunk fade, cutout, water/translucency, lighting, resource packs and modified sources; fall back to translated GLSL. A matching pipeline name is insufficient proof of compatible shader contents.

**Measure:** GPU shader time, register/varying statistics where available, equal-resolution frames and image differences, separately from occlusion and other mcopt defaults.

### D11. JNI overhead differs from critical FFM, but calls should first be removed

**Eviemetal:** JNI setters/draws generally use local native autorelease pools. Multi-draw already crosses the bridge once. [E-mtl], [E-native-draw], [E-native-core]

**mcopt:** cached FFM method handles mark many short native bindings/draws critical; allocation, submit and wait calls use other paths. This changes bridge/safepoint behavior but does not remove Metal driver work. [M-ffi], [M-native-draw]

**Consequence/confidence:** CPU bridge/API overhead; expected impact **low**, possibly **medium** in many-small-call scenes. Mechanism confidence **high**, material gain **uncertain** and JDK-dependent.

**Adaptation:** eliminate D3 redundancy, then batch remaining state+draw calls at an explicit native boundary. A bounded ordinary FFM leaf experiment can isolate bridge cost. Critical calls must be short, non-callback and unable to hide waits, compilation or long driver work. Preserve native ownership/draining if changing autorelease pools.

**Measure:** call counts, render-thread samples and bounded bridge microbenchmarks plus actual scenes; include safepoint/tail behavior, not just average call latency.

### D12. Range merging can reduce actual draws beyond bridge batching

**Eviemetal:** native multi-draw loops over each count/index-offset/base-vertex entry. Sodium already combines compatible directional runs when constructing batches, but no additional general adjacent-range merger was found at the bridge. [E-native-draw], [S-old-draw], [E-sodium]

**mcopt:** separate opt-in `DrawMerge`/mixin logic coalesces eligible adjacent shared-quad ranges on the plain route. Conditions include compatible zero-offset quad indexing, contiguous base vertices and bounded shared-index capacity; pulled/split terrain has another route. Its plain native multi-draw remains a loop. [M-draw-merge], [M-draw-merge-mixin], [M-native-draw], [S-new-renderer]

**Consequence/confidence:** fewer Metal draw calls versus added CPU scanning. Impact **medium** in submission-bound terrain, **low** when few ranges qualify. Confidence **high** in mechanism, **uncertain** in saved-call ratio.

**Adaptation:** preserve order and signed base-vertex semantics; prove the shared index pattern/capacity and disable merging for sorted translucency/arbitrary indexed data. Reuse merge scratch or merge during existing batch construction. Do not introduce a scan that consistently saves no calls.

**Measure:** input/output ranges, eligible fraction, merge CPU time, native draw count and GPU time. One JNI call before and after is not the relevant success metric.

### D13. Pulled indirect terrain and same-frame hi-Z are an architectural experiment

**Eviemetal:** Sodium submits ordinary indexed ranges after CPU visibility/face selection. No equivalent GPU quad-compaction/hi-Z pulled terrain path was found. [E-sodium], [E-native-draw], [S-old-draw]

**mcopt:** `MetalTerrain` groups quads, caches bounds, draws previously visible quads, builds same-frame hi-Z and compacts remaining eligible work into indirect instanced lists. Geometry is vertex-pulled from resident arenas. It limits arenas/flushes and keeps sorted translucent work elsewhere. Native indirect submission is not synonymous with a Metal indirect command buffer. [M-terrain], [M-native-terrain], [M-native-draw]

**Consequence/confidence:** potentially **high** CPU submission/vertex-work reduction in dense occluded scenes; impact **uncertain** overall because compute, pass splits, bounds memory and previous visibility work can lose at high target pixel counts. `MetalOccPick` includes resolution-aware forced-mode trials precisely because upstream testing found a crossover. Those measurements are workload-specific, not proof for Eviemetal. [M-occ]

**Adaptation:** first establish CPU versus GPU bottlenecks. Start with a narrow opaque/cutout plain-pulled experiment before hi-Z. Preserve order, arena lifetime/upload ordering, source fingerprints, generic fallback and translucent rendering. Rebuild conservative culling math for Eviemetal's clip/depth conventions; mcopt's reversed-depth reductions cannot be copied unchanged. Correct invalidation must cover arenas, shaders, camera, target size, world changes and retained previous visibility.

**Measure:** requested/kept quads, drawn vertices, all pre/main/compute GPU timings, CPU encoding, actual splits and cache bytes; compare open, cave, dense-city and fast-camera workloads at 1080p and native resolution. Force each path before validating any automatic selector.

### D14. CPU render-list and clone caches have different safety and memory costs

**Eviemetal:** retains pinned Sodium's collector/region lookup and cloned-section cache rather than importing mcopt CPU patches. Both inspected Sodium collector versions explicitly allow asynchronous traversal concurrent with region removal. [E-sodium-device], [S-old-collector], [S-new-collector], [S-old-clones]

**mcopt:** default alpha enables a last-region lookup cache and, on larger hardware tiers, 4096 clone entries plus expired-entry prefix cleanup. The list-cache comment claims stable render-thread traversal, but that claim conflicts with upstream's async contract. Optional `cullReuse` has multiple camera/graph/distance/pending/nearby checks; alpha's `cullRecover` redirties canceled work and is a correctness/freshness mechanism, not the same optimization. [M-list-cache], [M-clones], [M-clones-cleanup], [M-profile], [M-profile-alpha], [M-cull-reuse], [M-cull-mixin]

**Consequence/confidence:** potential lookup/rebuild CPU savings against stale-object risk, retained heap and GC. Impact **medium** on rebuild tails, **uncertain** for steady FPS. Confidence **high** in the contract mismatch, **medium** in clone benefit.

**Adaptation:** do not adopt the last-region cache until snapshot/generation/thread invariants are proved. Evaluate ordered prefix cleanup independently of capacity, and capacity under a heap budget/pressure test. Treat cull-recovery correctness separately from fewer cull tasks.

**Measure:** lookup CPU samples, async generations/removals, clone hit/miss/expiry counts, rebuild latency, heap/GC and visibility correctness while loading/unloading regions.

### D15. mcopt's chunk CPU patches are independent of its Metal backend

**Eviemetal:** uses Sodium's normal mesher/visibility/biome paths. Therefore ordinary Sodium meshing optimizations are already present, but mcopt's additional patches are not. [E-sodium-device], [S-old-mesher], [S-old-slice], [S-old-biome]

**mcopt:** alpha enables selected mesh patches: cached AIR slice arrays, branch/bit bounds logic, directional visibility work, reduced lambda/iterator work and uniform-biome fast paths. Integrated-server save/lighting/worldgen changes are additional subsystems, not client draw submission. [M-profile-alpha], [M-mesh-air], [M-mesh-bounds], [M-mesh-vis], [M-mesh-block], [M-mesh-biome], [M-chunk]

**Consequence/confidence:** worker CPU/allocation and chunk availability; impact **medium** during rebuilds, **low** on warmed stationary GPU-bound scenes. Source confidence **high**, effectiveness in a SkyBlock pack **uncertain**. Changes to JIT-sensitive branches need measurement on the actual JDK/ARM CPU.

**Adaptation:** choose one profile-confirmed isolated fast path in a versioned adapter. Preserve model/mod behavior, biome blending, visibility and task cancellation. Avoid broad overwrites merely to import a collection of upstream claims.

**Measure:** worker task CPU/wall time, allocation stacks, queue depth, upload bursts, chunk-appearance latency and image/visibility checks under custom models/biomes.

### D16. Arena reservation and residency trade memory for allocation tails

**Eviemetal:** shared buffer allocation uses ordinary native allocation; staging is budgeted. No equivalent large upfront terrain arena reserve/page wiring was found. [E-buffer], [E-ring], [E-native-resources]

**mcopt:** defaults to a 256 MiB arena reserve, allocated/touched in the background and managed with residency support. This targets first-use/page-fault allocation stalls; it is not evidence that ordinary shared storage leaks. Newer macOS APIs must be guarded for Eviemetal's macOS 14 floor. [M-device], [M-native-memory], [M-release]

**Consequence/confidence:** potential **medium** allocation-tail benefit versus higher retained/physical memory and pressure, **low** steady-state benefit; source confidence **high**, net gain **uncertain** on a 36 GB Mac or a small-memory machine.

**Adaptation:** begin with bounded completed resource reuse. Test a small optional reserve only after attributing stalls to page/allocation work; account for power/startup work, trimming and platform availability. Do not copy the size or wire memory universally.

**Measure:** allocation duration, page faults/compression, RSS/physical footprint, unused reserve and teleport/rebuild tails under memory pressure.

### D17. Presentation, caps and QoS affect pacing independently of renderer work

**Eviemetal:** asynchronous drawable prefetch with VSync off can skip presentation; VSync mode can acquire synchronously when necessary. GPU backlog waits are bounded. High logical FPS is not necessarily high displayed FPS. [E-native-frame], [E-frame-sync]

**mcopt:** late drawable acquisition, display pacing, default precise limiter and render-thread QoS alter pacing/scheduling. `profile=none` alone does not disable these defaults. Integrated-server and worker QoS are distinct policies. [M-surface], [M-frame-wait], [M-qos], [M-profile], [M-native-sleep]

**Consequence/confidence:** synchronization/frame-pacing and power effects; impact **medium** when capped or scheduling/drawables dominate, **low** for raw GPU throughput. Source confidence **high**, comparative effect **uncertain**.

**Adaptation:** separately test precise cap sleep, drawable timing and QoS. Keep input/present latency, power and other task responsiveness in acceptance criteria. Do not increase frames in flight to hide stalls without latency/memory evidence.

**Measure:** work/sleep/drawable/throttle times, rendered/submitted/presented counts, presented intervals, drawable age and power/thermal state at uncapped and 60/120/300 FPS caps.

### D18. Compatibility handling is narrow; deleting it is not a performance plan

**Eviemetal:** `LegacyGlStateMixin` uses a Metal-active guard and render-thread assertion before canceling obsolete GL state around explicit Minecraft rendering. It does not provide a second GL renderer, per-call stack inspection or universal direct LWJGL emulation. Generic shader compatibility processing occurs during compilation. Startup inventory decisions are cached. Existing GUI documentation records the actual Talium/Minecraft rendering relationship for Devonian and supported SkyHanni/SBPV cases. [E-gl], [E-glsl], [E-bootstrap], [E-gui-doc]

**mcopt:** newer explicit backend routing and bundled exact-name shaders avoid some old adaptation, but do not establish equivalent support for these older mod GUIs or arbitrary raw GL. Shader hooks/delegates also impose their own compatibility boundaries. [M-device], [M-encoder], [M-pipeline], [M-pass]

**Consequence/confidence:** guard overhead likely **low**, confidence **medium**; no full-pack timing was measured. Actual GUI geometry/items/uploads/setup may be material. Sodium adaptation has specific costs in D2/D3/D7, not proof that all compatibility is slow.

**Adaptation:** preserve compatibility. Count/profile guards separately from GUI work; specialize guarded wrappers or cache immutable decisions only if material. Preserve thread/order invariants and generic shader fallback. Do not treat working state cancellation as universal raw-OpenGL support.

**Measure:** wrapper calls/time, screen open/closed and HUD on/off under the same pack, correlated with setup/draw/upload work. Existing GUI fixture results are not new live SkyBlock tests.

### D19. Fans, views and resize are conditional low-priority targets

**Eviemetal:** nonindexed fans already use a cached growable sequential index buffer; arbitrary indexed fans expand into temporary storage. Ordinary texel views belong to buffers; Sodium time views refresh on storage changes. Resize changes drawable size and rejects stale prefetched drawables. Existing ownership/cleanup fixes are present. [E-pass], [E-buffer], [E-sodium], [E-native-frame]

**mcopt:** native fan handling assumes sequential indices. Default texel views are cached per pass, with optional longer-lived `TexelViews`. Surface resizing also invalidates optional hi-Z/work targets. [M-native-draw], [M-pass], [M-texel], [M-surface], [M-native-terrain]

**Consequence/confidence:** impact **low** normally, conditional on indexed-fan/view/resize frequency; confidence **medium**. mcopt's default per-pass view cache is not inherently superior to Eviemetal's buffer-owned cache.

**Adaptation:** retain arbitrary-index fan semantics. Pool scratch only if hot; framebuffer/view reuse requires format/mip/view identity and completion-safe ownership. Do not presume historical leaks survive the current fixes.

**Measure:** fan counts, target/view allocations, repeated GUI/fullscreen/resize transitions and post-unload memory plateaus.

### D20. Newer Sodium prepares index capacity before drawing

**Eviemetal:** Sodium 0.9.2 checks shared-index capacity inside the region-render loop. Growth mapping goes through `SodiumMetal.map()/suspendPass()`, closing logical terrain ownership and clearing bound-PSO state. CPU mapping does not itself perform a Metal blit; unchanged targets can still merge natively. [S-old-draw], [S-old-index], [E-sodium], [E-upload], [E-native-pass]

**mcopt:** Sodium 0.9.3-alpha.1 has a separate `prepare()` phase that fills batches and ensures shared-index capacity before terrain rendering. This is upstream scheduling, not an mcopt-native draw optimization, and does not eliminate generation/allocation. [S-new-renderer], [M-pass]

**Consequence/confidence:** extra logical setup during capacity growth; impact **low** in stable scenes, potentially **medium** at growth bursts. Scheduling confidence **high**, frame cost **uncertain**. A suspension does not prove attachment store/reload bandwidth.

**Adaptation:** if counters justify it, prepare required shared-index capacity before terrain via a narrow versioned adapter. Avoid unconditional extra region walks that cost more than saved setup; preserve cached-batch invalidation and indexed translucency. A future upstream upgrade may supply this directly.

**Measure:** growth events, logical suspensions versus real native splits, restored bindings and growth-frame CPU time during loads/rebuilds.

## Recommended optimizations

Establish the instrumentation/profiling gate below before substantial implementation. Benchmark one independent lever at a time. Ratings include ordering, rendering correctness, lifecycle and mod compatibility. New optional experiments should follow the repository's default-off and narrow-adapter principles; internal behavior-preserving optimizations still need image/order validation.

### Tier 1 — High-confidence / high-value

| Priority | Change and rationale | Expected impact | Difficulty | Risk | Likely affected metrics | Eviemetal subsystem / mcopt reference |
| --- | --- | --- | --- | --- | --- | --- |
| T1.1 | Canonicalize immutable terrain layouts and reuse structural PSO keys without per-region streams/records (D2) | Medium | Low | Low | CPU frame time, allocations/GC, 1% lows, CPU-bound average FPS | `SodiumMetal`, `SodiumRenderDevice.TerrainTessellation`; mcopt compiled pass/pipeline boundary |
| T1.2 | Pre-resolve terrain bindings and deduplicate handles/offsets/content generations (D3) | Medium | Medium | Medium | CPU submission, average FPS, 1% lows | `SodiumMetal.bindStage/multiDraw`, native encoder generation; mcopt pending/bound slots |
| T1.3 | Add complete GPU-use tracking, safe idle writes and bounded completed-storage spares (D1) | Medium | High | Medium | Upload CPU time, native memory, 1% lows, upload-heavy average FPS | `MetalBuffer`, `MetalCommandEncoder`, both pass paths; mcopt `use/idle/rename` |
| T1.4 | Prewarm observed attachment-format PSO variants and deduplicate compilation requests (D9) | Medium | Medium | Low | Cold/world/GUI 1% lows, frame pacing, load time | `MetalPipeline`, Sodium program cache, native compile; mcopt eager worker compilation |
| T1.5 | Use one copy for contiguous matching-format texture rectangles (D8) | Low | Low | Low | Upload CPU time, reload time, dynamic-texture frame tails | `MetalCommandEncoder.upload`; mcopt prepared contiguous source path |

T1.3 has the largest safety surface: a missed mapped/Sodium/copy/texel reference makes reuse incorrect. It should follow T1.1/T1.2 and counters, not be rushed because allocations are visibly redundant. T1.4 should compile a measured small set rather than every hypothetical combination, and must not evict externally referenced pipelines. T1.5 must preserve source strides and older component-conversion behavior.

### Tier 2 — Worth experimenting with

| Priority | Change and rationale | Expected impact | Difficulty | Risk | Likely affected metrics | Eviemetal subsystem / mcopt reference |
| --- | --- | --- | --- | --- | --- | --- |
| T2.1 | Merge eligible adjacent shared-quad ranges; preserve order/capacity and stop if scanning loses (D12) | Medium | Medium | Medium | CPU submission, native draws, average FPS | `SodiumMetal.multiDraw`, native loop; mcopt `DrawMerge` and mixin |
| T2.2 | Defer full clears, discard proven-dead stores and test folded depth clears (D5) | Medium | High | Medium | GPU frame time, bandwidth, average FPS, pacing | Java clears, native pass begin/end, read/copy boundaries; mcopt pending clears/dead stores |
| T2.3 | Batch ordered timestamps before their next observation or use fenced versions (D7) | Medium | High | High | 1% lows, CPU/GPU upload time, views, memory | Timestamp mixin, `TimeView`, `MetalBuffer`; mcopt newer resource/update route |
| T2.4 | Retain a bounded staging free list with pressure/idle trimming (D6) | Medium | Medium | Medium | Allocation tails, native memory, 1% lows | `UploadRing`; mcopt transient completion recycling |
| T2.5 | Resolve ordinary named resources once and skip unchanged reflection walks (D4) | Medium | Medium | Medium | GUI/entity CPU frame time, allocations, average FPS | `MetalRenderPass`, reflection; mcopt numbered dirty slots |
| T2.6 | Test lean terrain varying precision under full shader fingerprints (D10) | Medium | High | High | GPU frame time, bandwidth, average FPS | Sodium translator/program interface; mcopt bundled MSL |
| T2.7 | Batch remaining native calls; optionally isolate an ordinary FFM leaf experiment (D11) | Low | Medium | Medium | CPU submission, safepoint/tail behavior | `Mtl`, native bindings/draws; mcopt `Native` |
| T2.8 | Test ordered clone-cache cleanup before increasing capacity under a heap budget (D14) | Medium | Medium | Medium | Rebuild CPU time, heap/GC, 1% lows | Narrow pinned Sodium adapter; mcopt clone/cleanup patches |
| T2.9 | Choose one profiled mesher fast path rather than importing broad overwrites (D15) | Medium | High | High | Mesh task latency, chunk availability, 1% lows | Versioned Sodium CPU adapter; mcopt AIR/bounds/visibility/lambda/biome patches |
| T2.10 | Separately test precise cap sleep, drawable selection and render-thread QoS (D17) | Medium | Medium | Medium | Capped pacing, 1% lows, power, input latency | Presentation and limiter adapter; mcopt surface/FrameWait/Qos |
| T2.11 | Prepare shared-index capacity before terrain only if growth counters justify it (D20) | Low | Medium | Medium | Load/rebuild CPU time, 1% lows | Narrow Sodium batch/index adapter; newer Sodium `prepare()` |

Render-list lookup reuse from D14 is **not ready for this tier** until asynchronous mutation semantics are resolved. A proved snapshot/generation check could make it a small CPU experiment. A comment claiming stable traversal is insufficient. Shader precision and timestamp batching need stronger image/order gates than ordinary layout caching.

### Tier 3 — Longer-term architecture

| Priority | Change and rationale | Expected impact | Difficulty | Risk | Likely affected metrics | Eviemetal subsystem / mcopt reference |
| --- | --- | --- | --- | --- | --- | --- |
| T3.1 | Prototype vertex-pulled indirect opaque/cutout terrain, then same-frame hi-Z only after forced-mode evidence (D13) | High | High | High | CPU submission, GPU vertex time, average FPS, memory | Sodium batch extraction, arena ownership, native compute/indirect; mcopt `MetalTerrain/mcterrain.m` |
| T3.2 | Evaluate a small optional page-prepared/resident arena reserve only for attributed allocation faults (D16) | Medium | High | Medium | Allocation-tail 1% lows, physical memory, startup/power | Native buffer allocator, platform guards, pressure trim; mcopt reserve/residency implementation |
| T3.3 | Use a future Minecraft/Sodium explicit API upgrade to reduce old program/handle adaptation while retaining compatibility (D2/D3/D7/D20) | Medium | High | High | CPU setup, maintainability, uploads, frame tails | Isolated terrain device/program adapter; mcopt renderpearl boundary and newer Sodium |

T3.1's **High** rating is an upper opportunity for dense submission/visibility-limited terrain, not a universal gain. Open low-distance scenes or high-resolution fragment/compute-bound scenes can lose. T3.3 should be driven by an actual supported version migration, not redesigning the current renderer solely to resemble mcopt.

## Things not worth copying

| Technique / apparent advantage | Why Eviemetal should not copy it wholesale |
| --- | --- |
| Delete legacy compatibility or assume newer mcopt supports the same GUIs | Eviemetal's narrow state shim has a concrete rendering contract and user-reported working cases. Removing it breaks useful mods; profile guard work first. [E-gl], [E-gui-doc], [M-device] |
| Treat Sodium support as future work | It is already implemented. Newer Sodium's explicit API is upgrade evidence, not a missing integration. [E-sodium-device], [S-new-renderer] |
| Mark every bridge call critical or remove all autorelease pools | Blocking/allocating/compiling/long driver work does not fit a bounded critical leaf contract. Ownership still needs a drain policy. [E-native-core], [M-ffi] |
| Override shaders solely by pipeline name | Resource packs/mods can alter source/defines/layout under the same name; bundled precision/fade changes require full fingerprints and fallback. [E-glsl], [M-pipeline], [M-terrain-vs] |
| Enable hi-Z/pulled terrain everywhere | Added compute/splits/cache traffic can lose; mcopt itself selects adaptively. Sorted translucency and incompatible shaders need separate paths. [E-sodium], [M-terrain], [M-occ] |
| Cache the last region on the assumption traversal cannot race removal | Both pinned collectors describe async mutation; stale pointers are a correctness risk. [S-old-collector], [S-new-collector], [M-list-cache] |
| Replace arbitrary indexed fans with sequential fans | Mod indices can be nonsequential. Eviemetal already caches the safe nonindexed case. [E-pass], [M-native-draw] |
| Copy the 256 MiB reserve or 4096 clones universally | Higher retained/physical memory can worsen pressure and GC. mcopt already gates some clone settings by hardware tier. [E-ring], [M-native-memory], [M-profile] |
| Add another command buffer as an optimization by itself | A pre buffer has purpose in mcopt's pulled path; extra commits alone add queue/lifetime work. [E-native-frame], [M-native-submit], [M-terrain] |
| Apply `DontCare`/memoryless to arbitrary targets | Sampling, copies, views, resize and presentation can require contents. Only proven-dead attachments qualify. [E-native-pass], [M-native-pass] |
| Convert all buffers to private storage | Both renderers rely on coherent shared buffers. mcopt's private setup index data does not prove persistent arena/GUI buffers benefit from extra copies. [E-buffer], [M-buffer], [M-native-resources] |
| Adopt argument buffers as a named missing feature | Neither inspected common binding path establishes an argument-buffer advantage. First remove actual redundant bindings and measure remaining slots/calls. [E-pass], [E-sodium], [M-pass] |
| Count frame generation/scaling as equal-quality renderer FPS | Generated frames and lower internal resolution change output/work. Benchmark separately with real render/present counts and image quality. [M-fx], [M-framegen] |
| Use LOD to claim a like-for-like renderer win | Geometry/distance/quality changes and extra far-terrain work answer another question. Keep it off in core comparisons. [M-profile], [M-readme] |
| Attribute integrated-server/worldgen/save gains to remote SkyBlock rendering | Those tasks may be absent on a remote server. Client meshing/visibility still matters. [E-sodium-device], [M-chunk], [M-profile-alpha] |
| Treat preload/narrator startup improvements as steady rendering wins | They can affect launch time or retained resources but do not directly reduce steady draw submission. [M-preload], [M-profile-alpha] |
| Rank published FPS across mismatched environments | Version, hardware, target pixels, settings, mods/profiles, caps and presentation differ. [E-build], [M-build], [M-readme] |

Reference implementation does not imply permission to transplant every file. mcopt's NOTICE marks some bundled terrain MSL and Sodium-derived CPU patches as PolyForm Shield, while other files are Apache-2.0; Eviemetal has existing attribution/license boundaries. Preserve provenance and establish applicable distribution terms before importing protected portions. This report adds no borrowed implementation. [M-notice]

## Benchmark plan

### Matrix and version control

Use one physical Apple Silicon Mac, the same macOS build and Java 25 vendor/patch, power mode and display arrangement for all runnable cells. Start on the user's normal hardware, then repeat promising changes on a smaller-memory/GPU Mac if available. Record chip, core counts, RAM and display refresh explicitly rather than inferring them from a product name.

| Cell | Minecraft / Sodium | Backend and purpose |
| --- | --- | --- |
| A | 26.1.2, no Sodium | Fabric/vanilla OpenGL baseline |
| B | 26.1.2, Sodium 0.9.2 | Sodium/OpenGL baseline |
| C | 26.1.2, no Sodium | Eviemetal; isolate backend without terrain frontend |
| D | 26.1.2, Sodium 0.9.2 | Eviemetal + Sodium; primary optimization baseline |
| D-N | Same as D | One proposed Eviemetal optimization at a time |
| E | 26.3, no Sodium | Closest stock backend baseline actually available; record backend |
| F | 26.3, Sodium 0.9.3-alpha.1 | Closest stock Sodium/backend baseline actually available |
| G | 26.3, Sodium 0.9.3-alpha.1 | mcopt core with unrelated features/defaults explicitly disabled |
| H | Same as G | mcopt shipping default profile, reported separately |
| G-N | Same as G | One mcopt reference lever at a time, with effective flags recorded |

Treat A–D and E–H as separate version groups. The D-to-D-N causal test is stronger than D-versus-G: the latter retains Minecraft/Sodium/API differences even on identical hardware. If 26.3 cannot expose a requested stock backend, mark that matrix cell unavailable and identify the actual available baseline; never label fallback Vulkan/another backend OpenGL. Verify the selected backend in logs each run.

Use matching committed fixture worlds/routes where conversion is possible, keep separate converted copies and preserve original saves. Identical seed is not sufficient when world generation/lighting/model versions differ. Record block state/geometry, chunks loaded and screenshots, plus camera/time/weather. Explain any unavoidable visual differences. A newer upstream version is not a renderer optimization arm.

For G, a fresh instance must explicitly establish effective options. `profile=none` disables the profile, not every separately enabled default. Begin with:

```text
-Dmcopt.profile=none
-Dmcopt.preload=false
-Dmcopt.qos=off
-Dmcopt.preciseLimiter=false
-Dmcopt.metal.pace=false
-Dmcopt.metal.occ=false
-Dmcopt.metal.builtinMsl=false
-Dmcopt.metal.arenaReserve=0
-Dmcopt.fx.scale=1
-Dmcopt.metal.framegen=false
-Dmcopt.lod=false
-Dmcopt.rec=false
```

Do not load pack/shading redirects; archive effective config, mod list and logs. Test display pacing separately. H should preserve the release's shipping alpha profile/hardware-tier choices, but remain equal internal resolution with frame generation/LOD off for the primary quality comparison; any altered shipping feature gets its own labeled arm. Independently enable bundled MSL, range merging, forced pulled/hi-Z, reserve, mesher/cache patches, limiter and QoS to isolate contributions. Flags are grounded in the inspected configuration sources, not a claim that these remove every unrelated effect. [M-profile], [M-profile-alpha], [M-pipeline], [M-terrain], [M-device], [M-qos], [M-frame-wait], [M-fx], [M-framegen]

### Run manifest and controls

Each result bundle should include exact repository commits/JAR hashes, Minecraft/Fabric/Sodium versions, full mod/pack versions, effective backend/options, Java vendor/build, JVM flags, heap limits/GC, OS build and display arrangement. Keep heap and GC equal unless testing memory specifically. Avoid comparing one JVM with extra agents/validation/profiling against another without them.

Record internal framebuffer pixel dimensions, window dimensions/UI scale, Retina backing scale, fullscreen mode, render/simulation/entity distance, graphics/clouds/particles, biome blend, mipmaps, translucency/OIT, shadows or shader packs, FOV and camera path. A 1080p external display and a high-pixel-count built-in screen are distinct GPU workloads; “same window size” is not enough. Use at least 1080p and actual native backing resolution.

Control AC/battery, low-power mode, charge state, thermal stabilization, background applications, display refresh and VSync/cap. Primary throughput: uncapped/VSync off, with presented versus rendered counts. Separate pacing: VSync and 60/120/300 FPS caps as supported, using identical caps and clearly identifying display refresh. A 60 Hz display cannot validate 300 presented frames/s, but can still expose capped CPU/GPU work and logical frame intervals.

Disable Metal validation/debug overlays, verbose counters, recorders and screen recording for primary throughput. Capture with tools in separate diagnostic runs; A/B any minimal recorder to quantify its own overhead. Reset to the same fixture and initial camera for every repetition; no resource reload mid-steady phase unless that is the designated workload.

### Workloads and repetition

Prefer at least **five independent runs per arm**, balanced/randomized order, with a fresh process for run-level independence. Warm for about **five minutes**, then measure **180–300 seconds** of repeatable steady/routes. Stabilize temperatures; randomize ordering rather than always testing the proposed optimization after warmup. Use longer runs if noise overwhelms the expected effect.

| Workload | Design | Separates |
| --- | --- | --- |
| Static open terrain | Fixed camera/time/weather at low and high distance | Submission versus open-scene vertex/fragment cost |
| Dense city/cave | Occluded geometry with fixed camera and repeated route | CPU visibility versus GPU hi-Z opportunity |
| Fixed camera movement | Replayed smooth and fast turns through the same chunks | List invalidation, ordering, frame pacing |
| Rebuild/upload stress | At least five full rebuilds per arm with event windows | Mesher, staging, timestamp updates and allocation |
| Teleports/loading | At least 20 reproducible transitions; distinguish unloaded/warm destinations | Upload bursts, arena growth, view refresh and shader first use |
| GUI/item workload | At least 20 open/close cycles with matched contents; inventory/HUD/Devonian settings | Ordinary setup, compatibility guards, texture and item draws |
| Texture reload/animation | Fixed resource pack and explicit reload phases | Packing/copy versus shader/PSO reload costs |
| Endurance | 20–30 minutes with unload/reload and repeated screens | Retained plateaus, memory pressure and late tails |
| Resize/fullscreen | Repeated known pixel sizes and return to baseline | Drawable/target recreation and stale work |

Run vanilla/minimal instances first, then Eviemetal's real pack with pinned SkyHanni, SBPV and Devonian/Talium versions. The exact same pack may not run on 26.3; disclose unavailable cells instead of substituting different mods silently. Profile HUD/GUI on/off within the same instance to isolate compatibility and UI work.

Live Hypixel dungeons are valuable acceptance evidence but network/player/server changes prevent deterministic comparison. Use longer matched sessions and record events, then label them observational. A 60-second dungeon sample or a two-second debug FPS graph is insufficient to attribute a small architectural improvement. Existing compatibility fixture results and user-reported working screens are context, not newly reproduced benchmarks.

### Metrics and statistics

Retain raw frame intervals and phase/event tags, not just an FPS counter. State whether a frame means a logical game frame, submitted GPU work or displayed presentation; report all available boundaries rather than substituting one for another.

- **Average FPS:** number of measured frames divided by total measured elapsed seconds, equivalently `N / sum(frame intervals)`. Do not average instantaneous FPS samples.
- **Median FPS:** define as reciprocal of median frame time, with units stated. Also report median frame time directly.
- **1% low:** explicitly define as the reciprocal of the mean of the worst `ceil(0.01 × N)` frame times. Keep that distinct from reciprocal p99 frame time and disclose the chosen convention.
- **Distribution:** p50/p90/p95/p99/p99.9/max, histogram and time series; counts above 16.67/33.33/50/100 ms. Separate loading/transition phases from steady phases.
- **CPU:** game/render/submission wall time and thread CPU time where available, separated from limiter sleep, drawable wait, GPU throttle, scheduling and GC. JFR/CPU sampling helps attribution.
- **GPU:** execution timeline for every command buffer associated with the frame, including pre work and extra fence-triggered submits. Keep queue delay/present/compositor apart. Overlapping encoder stages are not additive independent critical-path costs.
- **Memory:** heap used/committed, allocation rate, GC pauses, RSS/physical footprint, compression/page faults, native Metal allocations and staging/cache/reserve high water, with post-unload plateaus. Unified-memory categories overlap; do not blindly add CPU and GPU gauges.
- **Pacing:** rendered/submitted/presented counts, skipped presents, presented intervals, drawable age/wait and flight-throttle time. Callback timestamp is not scanout or input latency.

Publish per-run summaries, raw intervals and manifests with run-level confidence intervals. Frames are correlated; millions of frames in one run are not millions of independent replications. Keep legitimate GC/uploads/compile hitches in the relevant workload. Exclusions must be predeclared and symmetric.

Success is a repeatable change larger than run variation, with the predicted operation/time reduction and no visual, compatibility or pressure regression. A low-impact optimization may be accepted for measured CPU/allocation savings even when whole-game FPS does not resolve; report that limitation.

### Warm and cold shader/pipeline behavior

Keep three cases distinct:

1. **Warm app/driver caches:** repeated launches/transitions; record translation hits and first-use variants.
2. **Cold application translation cache:** move only the dedicated cache in a disposable instance. Eviemetal uses `eviemod-metal/shader-cache`; mcopt's optional cache uses `mcopt-cache/msl`. This does not prove Apple's driver cache is cold.
3. **Likely cold driver/first install or OS update:** separate observed case with uncertainty. mcopt's `uncachedMsl=true` can perturb source, but Eviemetal has no matched switch; do not compare that against a warm Eviemetal run or wipe system-wide caches.

Measure translation, Metal library compilation and PSO creation independently and tag the first consuming frame. Cold stall reduction and warm throughput answer different questions. No cache-control code is implemented here. [E-shader-cache], [E-pipeline], [M-pipeline]

## Profiling instrumentation to add later

### Existing measurements and limits

Eviemetal already exposes `MetalQuery`, `Mtl.gpuNanosBetween`, `takeGpuSeconds`, driver allocation diagnostics and opt-in pass profiling (`-Deviemod-metal.gpuProfile=true`). GPU timing is not wholly missing. Pass profiling aggregates merged labels and estimates timeline extension, not isolated inclusive encoder cost. No production consumer of `takeGpuProfile()` was found, so usable export/attribution is missing. [E-query], [E-mtl], [E-native-timing], [E-native-commit]

Before using these as a reliable per-frame exporter, address two points:

- Timing history is written by a completion handler, while readiness first checks the shared event. The event can signal before its history record is published, and missing entries are skipped. A ready query can therefore yield an incomplete/zero sum. Publish timing readiness independently and treat missing records as unavailable, not zero.
- Fence queries bracket the next commit, and history has 1024 entries. Extra commits, logical-frame attribution and delayed polling can complicate boundaries or expire entries. Record explicit frame/submit membership and unavailable/expired state rather than treating F3 GPU percentage as precise frame execution time.

`Mtl.allocatedBytes()` waits for the last submitted command buffer. That is useful at quiescent lifecycle boundaries, but serializes a throughput run if sampled continuously. Add a nonblocking gauge or clearly labeled phase-boundary sampling later. [E-native-resources], [E-native-timing], [E-native-commit]

mcopt has optional traces/events/GPU cadence arrays and custom command-buffer diagnostics. Its standardized timestamp query pool returns no results and `writeTimestamp()` is empty. Main-command-buffer timings alone miss optional pre work; fixed diagnostic capacities must disclose overflow. An implemented interface does not prove reliable standardized timestamps. [M-device], [M-pass], [M-gpu-times], [M-events], [M-encoder]

### Minimal default-disabled recorder

Use bounded fixed-size primitive rings per logical frame and native submit, with export once per phase/end. No per-draw logging, formatting, maps or file IO. Completion handlers publish bounded native timing records without invoking game-state Java; the render thread polls/exports. Include capacity, dropped records, unavailable/expired samples and timer support. Measure recorder-off versus recorder-on overhead.

| Measurement | Boundary / subsystem | Question answered |
| --- | --- | --- |
| Logical frame ID, work start/end, phase, cap/backend | Minecraft boundary before limiter/after work | Comparable CPU work interval and workload membership |
| CPU extraction/setup/submission wall and thread time | Coarse game/render boundaries, JFR/thread clocks | Game work versus backend encoding, preemption and GC |
| Requested draws/batches and actual native draws | Ordinary draw methods, Sodium/native loop | Fewer Metal draws or merely fewer bridge calls? |
| Pipeline lookups/hits/misses/variants/switches | Device/program cache and native setters | Allocation/compile/state cost and bounded cache behavior |
| Translate/library/PSO time, first consumer frame | Compiler boundaries | Which cold/warm stage hitches? |
| Binding attempted/actual/skipped, bytes by stage/type | Ordinary/terrain caches and native setters | Dedup/dirty-plan benefit |
| Logical passes, real render/blit/compute encoders, merges/split reasons | Java ownership plus actual native begin/end | Which boundaries cause target traffic? |
| Attachment dimensions/formats/load/store/discard | Native descriptor/end | Bandwidth changes, not inferred from pass count |
| Command-buffer IDs/counts, commit and frame membership | Native lazy creation/commit/fence submission | Extra submits, queue work and attribution |
| Patched/copied/uploaded bytes by buffer/texture/time category | Upload/packing/copy helpers | Excess bandwidth and four-byte update amplification |
| Buffer/texture/view allocations/releases, orphan/spare hits | Native ownership helpers | Churn versus retained memory |
| Staging used/retained/high-water, waits/timeouts | `UploadRing` | Backpressure versus allocation tails |
| Storage generation, mapped state, last GPU read/write use | All references, rename/close | Validate idle/spare safety |
| Chunk rebuild count/task time/queue/upload/arena growth | Optional pinned Sodium adapter | Worker meshing versus submission/upload |
| Clone/list hit/miss/expiry/generation/thread | Only when testing those patches | Cache benefit and concurrent correctness |
| Wait duration by limiter/drawable/throttle/fence reason | Java/native waits | Work versus synchronization/cap |
| GPU start/end/readiness/queue timing and stage counters | Completion/counter resolution, nonblocking | Execution, missing samples and scheduling |
| Requested/skipped presents, drawable age/presented timestamps | Prefetch/take/present callbacks | Logical throughput versus displayed pacing |
| Legacy wrapper calls/canceled/unsupported aggregate | Compatibility boundary | Guard overhead versus actual GUI work |
| Heap/RSS/native gauges and unload markers | JFR/external/nonblocking or boundary samples | Budgets, plateaus and pressure correlation |

For future GPU terrain add requested/kept quads, all pre/main GPU intervals, compute dispatches, bounds invalidation/cache bytes, selected mode, target pixels and arena/held-copy ownership. A draw-count drop alone does not prove a GPU win.

Use Xcode Metal captures/System Trace in separate runs to inspect loads/stores, queue scheduling and shader costs; use JFR/CPU sampling for Java setup/allocation/GC. Unsupported hardware counters stay unavailable. No instrumentation is implemented in this task.

## Suggested implementation sequence and acceptance gates

1. Establish D with repeatable fixtures, trustworthy frame/submit attribution and upload/binding/allocation counters.
2. Land T1.1 and T1.2 independently; expect fewer allocations/bindings with unchanged PSO count/images/unload behavior.
3. Prototype T1.3 only after every reference is tracked; verify partial updates, mappings, timestamp view refresh and completion-safe spare reuse. Measure bursts and memory plateaus.
4. Use actual draws/attachment traffic to choose T2.1 or T2.2. A cap or setup bottleneck can make GPU occlusion irrelevant.
5. Test timestamp/staging changes on rebuilds/teleports, retaining ordered fallback and memory bounds.
6. Evaluate shader precision and pulled/hi-Z only with forced-mode timing, full GPU timelines, image checks and current compatibility/translucency preserved.

Every change should have one controlled A/B and a counter-level explanation. If it loses in common pack/resolution/power modes, keep it optional or reject it. Resemblance to mcopt is not an acceptance criterion.


## Source index

All links pin the inspected commits. Symbols in each finding identify the actual hot path; narrower native ranges separate submission, resource, draw and timing mechanisms. Eviemetal Java files are under `metal-addon/src/main/java/dev/eviemod/metal/`; mcopt Java files under `metal/src/main/java/mcopt/metal/`. Upstream Sodium is supporting evidence, not a substitute for inspecting the adapters.

| Reference | File and lines |
| --- | --- |
| [E-bootstrap] | `metal-addon/src/main/java/dev/eviemod/metal/MetalBootstrap.java` L1–L72 |
| [E-buffer] | `metal-addon/src/main/java/dev/eviemod/metal/device/MetalBuffer.java` L1–L84 |
| [E-build] | `metal-addon/build.gradle` L1–L111 |
| [E-device] | `metal-addon/src/main/java/dev/eviemod/metal/device/MetalDevice.java` L1–L212 |
| [E-frame-sync] | `metal-addon/src/main/java/dev/eviemod/metal/compat/sodium/SodiumFrameSync.java` L1–L27 |
| [E-gl] | `metal-addon/src/main/java/dev/eviemod/metal/mixin/LegacyGlStateMixin.java` L1–L88 |
| [E-glsl] | `metal-addon/src/main/java/dev/eviemod/metal/shader/GlslCompatibility.java` L1–L234 |
| [E-gui-doc] | `docs/metal-gui-compatibility.md` L1–L143 |
| [E-mtl] | `metal-addon/src/main/java/dev/eviemod/metal/mtl/Mtl.java` L1–L76 |
| [E-native-clear] | `metal-addon/src/main/native/eviemod_metal.mm` L660–L691 |
| [E-native-commit] | `metal-addon/src/main/native/eviemod_metal.mm` L143–L188 |
| [E-native-core] | `metal-addon/src/main/native/eviemod_metal.mm` L1–L188 |
| [E-native-draw] | `metal-addon/src/main/native/eviemod_metal.mm` L609–L715 |
| [E-native-frame] | `metal-addon/src/main/native/eviemod_metal.mm` L719–L839 |
| [E-native-pass] | `metal-addon/src/main/native/eviemod_metal.mm` L563–L607 |
| [E-native-resources] | `metal-addon/src/main/native/eviemod_metal.mm` L337–L440 |
| [E-native-shaders] | `metal-addon/src/main/native/eviemod_metal.mm` L461–L560 |
| [E-native-timing] | `metal-addon/src/main/native/eviemod_metal.mm` L761–L839 |
| [E-pass] | `metal-addon/src/main/java/dev/eviemod/metal/device/MetalRenderPass.java` L1–L320 |
| [E-pipeline] | `metal-addon/src/main/java/dev/eviemod/metal/device/MetalPipeline.java` L1–L223 |
| [E-properties] | `build.gradle` L1–L70 |
| [E-query] | `metal-addon/src/main/java/dev/eviemod/metal/device/MetalQuery.java` L1–L34 |
| [E-ring] | `metal-addon/src/main/java/dev/eviemod/metal/device/UploadRing.java` L1–L95 |
| [E-shader-cache] | `metal-addon/src/main/java/dev/eviemod/metal/shader/ShaderCache.java` L1–L129 |
| [E-sodium] | `metal-addon/src/main/java/dev/eviemod/metal/compat/sodium/SodiumMetal.java` L1–L519 |
| [E-sodium-device] | `metal-addon/src/main/java/dev/eviemod/metal/compat/sodium/SodiumRenderDevice.java` L1–L115 |
| [E-times] | `metal-addon/src/main/java/dev/eviemod/metal/mixin/sodium/SectionTimeUploadsMixin.java` L1–L19 |
| [E-translator] | `metal-addon/src/main/java/dev/eviemod/metal/shader/ShaderTranslator.java` L1–L242 |
| [E-upload] | `metal-addon/src/main/java/dev/eviemod/metal/device/MetalCommandEncoder.java` L1–L275 |
| [M-buffer] | `metal/src/main/java/mcopt/metal/MetalBuffer.java` L1–L77 |
| [M-build] | `metal/build.gradle` L1–L54 |
| [M-chunk] | `metal/src/main/java/mcopt/metal/chunk/ChunkOpt.java` L1–L124 |
| [M-clones] | `metal/src/main/java/mcopt/metal/mixin/chunk/ClonedChunkSectionCacheMixin.java` L1–L57 |
| [M-clones-cleanup] | `metal/src/main/java/mcopt/metal/mixin/chunk/ClonedChunkSectionCacheCleanupMixin.java` L1–L104 |
| [M-cull-mixin] | `metal/src/main/java/mcopt/metal/mixin/cpu/CullReuseManagerMixin.java` L1–L130 |
| [M-cull-reuse] | `metal/src/main/java/mcopt/metal/cpu/CullReuse.java` L1–L430 |
| [M-device] | `metal/src/main/java/mcopt/metal/MetalDevice.java` L1–L207 |
| [M-draw-merge] | `metal/src/main/java/mcopt/metal/cpu/DrawMerge.java` L1–L74 |
| [M-draw-merge-mixin] | `metal/src/main/java/mcopt/metal/mixin/cpu/DrawMergeMixin.java` L1–L40 |
| [M-encoder] | `metal/src/main/java/mcopt/metal/MetalEncoder.java` L1–L593 |
| [M-events] | `metal/src/main/java/mcopt/metal/MetalEvents.java` L1–L66 |
| [M-ffi] | `metal/src/main/java/mcopt/metal/Native.java` L1–L251 |
| [M-frame-wait] | `metal/src/main/java/mcopt/metal/FrameWait.java` L1–L14 |
| [M-framegen] | `metal/src/main/java/mcopt/metal/FrameGen.java` L1–L766 |
| [M-fx] | `metal/src/main/java/mcopt/metal/MetalFx.java` L1–L132 |
| [M-gpu-times] | `metal/src/main/java/mcopt/metal/GpuTimes.java` L1–L134 |
| [M-list-cache] | `metal/src/main/java/mcopt/metal/mixin/cpu/ListRegionCacheMixin.java` L1–L32 |
| [M-mesh-air] | `metal/src/main/java/mcopt/metal/mixin/chunk/LevelSliceAirMixin.java` L1–L83 |
| [M-mesh-biome] | `metal/src/main/java/mcopt/metal/mixin/chunk/LevelBiomeSliceMixin.java` L1–L62 |
| [M-mesh-block] | `metal/src/main/java/mcopt/metal/mixin/chunk/BlockRendererMixin.java` L1–L70 |
| [M-mesh-bounds] | `metal/src/main/java/mcopt/metal/mixin/chunk/LevelSliceBoundsMixin.java` L1–L62 |
| [M-mesh-vis] | `metal/src/main/java/mcopt/metal/mixin/chunk/DirectionalVisGraphMixin.java` L1–L51 |
| [M-native-clear] | `metal/src/main/native/mcmetal.m` L669–L807 |
| [M-native-draw] | `metal/src/main/native/mcmetal.m` L809–L1026 |
| [M-native-memory] | `metal/src/main/native/mcmetal.m` L130–L292 |
| [M-native-pass] | `metal/src/main/native/mcmetal.m` L427–L477 |
| [M-native-resources] | `metal/src/main/native/mcmetal.m` L260–L426 |
| [M-native-sleep] | `metal/src/main/native/mcmetal.m` L1027–L1161 |
| [M-native-submit] | `metal/src/main/native/mcmetal.m` L427–L575 |
| [M-native-terrain] | `metal/src/main/native/mcterrain.m` L1–L458 |
| [M-notice] | `NOTICE` L1–L28 |
| [M-occ] | `metal/src/main/java/mcopt/metal/MetalOccPick.java` L1–L59 |
| [M-pass] | `metal/src/main/java/mcopt/metal/MetalRenderPass.java` L1–L386 |
| [M-pipeline] | `metal/src/main/java/mcopt/metal/MetalPipeline.java` L1–L363 |
| [M-preload] | `metal/src/main/java/mcopt/metal/Preload.java` L1–L58 |
| [M-profile] | `metal/src/main/java/mcopt/metal/Profile.java` L1–L147 |
| [M-profile-alpha] | `metal/src/main/resources/mcopt/profiles/alpha.properties` L1–L17 |
| [M-properties] | `gradle.properties` L1–L9 |
| [M-qos] | `metal/src/main/java/mcopt/metal/Qos.java` L1–L85 |
| [M-readme] | `README.md` L1–L56 |
| [M-release] | `RELEASE-README.txt` L1–L37 |
| [M-surface] | `metal/src/main/java/mcopt/metal/MetalSurface.java` L1–L91 |
| [M-terrain] | `metal/src/main/java/mcopt/metal/MetalTerrain.java` L1–L528 |
| [M-terrain-fs] | `metal/src/main/resources/mcopt/metal/msl/sodium_terrain.fs.metal` L1–L69 |
| [M-terrain-vs] | `metal/src/main/resources/mcopt/metal/msl/sodium_terrain.vs.metal` L1–L110 |
| [M-texel] | `metal/src/main/java/mcopt/metal/TexelViews.java` L1–L62 |
| [M-transient] | `metal/src/main/java/mcopt/metal/MetalTransientMemory.java` L1–L134 |
| [S-new-collector] | `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/lists/VisibleChunkCollector.java` L1–L66 |
| [S-new-context] | `common/src/main/java/net/caffeinemc/mods/sodium/client/gpu/device/context/GLDrawContext.java` L1–L26 |
| [S-new-draw] | `common/src/main/java/net/caffeinemc/mods/sodium/client/gpu/device/batch/GLDrawBatch.java` L1–L46 |
| [S-new-renderer] | `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.java` L1–L391 |
| [S-old-biome] | `common/src/main/java/net/caffeinemc/mods/sodium/client/world/biome/LevelBiomeSlice.java` L1–L256 |
| [S-old-clones] | `common/src/main/java/net/caffeinemc/mods/sodium/client/world/cloned/ClonedChunkSectionCache.java` L1–L79 |
| [S-old-collector] | `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/lists/VisibleChunkCollector.java` L1–L66 |
| [S-old-draw] | `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.java` L1–L367 |
| [S-old-index] | `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.java` L1–L144 |
| [S-old-mesher] | `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask.java` L1–L300 |
| [S-old-slice] | `common/src/main/java/net/caffeinemc/mods/sodium/client/world/LevelSlice.java` L1–L404 |
| [S-old-stage] | `common/src/main/java/net/caffeinemc/mods/sodium/client/gl/arena/staging/MappedStagingBuffer.java` L1–L211 |
| [S-old-times] | `common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager.java` L1–L245 |

[E-bootstrap]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/MetalBootstrap.java#L1-L72
[E-buffer]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/device/MetalBuffer.java#L1-L84
[E-build]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/build.gradle#L1-L111
[E-device]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/device/MetalDevice.java#L1-L212
[E-frame-sync]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/compat/sodium/SodiumFrameSync.java#L1-L27
[E-gl]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/mixin/LegacyGlStateMixin.java#L1-L88
[E-glsl]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/shader/GlslCompatibility.java#L1-L234
[E-gui-doc]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/docs/metal-gui-compatibility.md#L1-L143
[E-mtl]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/mtl/Mtl.java#L1-L76
[E-native-clear]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/native/eviemod_metal.mm#L660-L691
[E-native-commit]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/native/eviemod_metal.mm#L143-L188
[E-native-core]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/native/eviemod_metal.mm#L1-L188
[E-native-draw]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/native/eviemod_metal.mm#L609-L715
[E-native-frame]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/native/eviemod_metal.mm#L719-L839
[E-native-pass]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/native/eviemod_metal.mm#L563-L607
[E-native-resources]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/native/eviemod_metal.mm#L337-L440
[E-native-shaders]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/native/eviemod_metal.mm#L461-L560
[E-native-timing]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/native/eviemod_metal.mm#L761-L839
[E-pass]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/device/MetalRenderPass.java#L1-L320
[E-pipeline]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/device/MetalPipeline.java#L1-L223
[E-properties]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/build.gradle#L1-L70
[E-query]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/device/MetalQuery.java#L1-L34
[E-ring]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/device/UploadRing.java#L1-L95
[E-shader-cache]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/shader/ShaderCache.java#L1-L129
[E-sodium]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/compat/sodium/SodiumMetal.java#L1-L519
[E-sodium-device]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/compat/sodium/SodiumRenderDevice.java#L1-L115
[E-times]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/mixin/sodium/SectionTimeUploadsMixin.java#L1-L19
[E-translator]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/shader/ShaderTranslator.java#L1-L242
[E-upload]: https://github.com/eveternet/eviemod/blob/100806d45011cbe922a8f2091cea703311c4e9cd/metal-addon/src/main/java/dev/eviemod/metal/device/MetalCommandEncoder.java#L1-L275
[M-buffer]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalBuffer.java#L1-L77
[M-build]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/build.gradle#L1-L54
[M-chunk]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/chunk/ChunkOpt.java#L1-L124
[M-clones]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/chunk/ClonedChunkSectionCacheMixin.java#L1-L57
[M-clones-cleanup]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/chunk/ClonedChunkSectionCacheCleanupMixin.java#L1-L104
[M-cull-mixin]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/cpu/CullReuseManagerMixin.java#L1-L130
[M-cull-reuse]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/cpu/CullReuse.java#L1-L430
[M-device]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalDevice.java#L1-L207
[M-draw-merge]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/cpu/DrawMerge.java#L1-L74
[M-draw-merge-mixin]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/cpu/DrawMergeMixin.java#L1-L40
[M-encoder]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalEncoder.java#L1-L593
[M-events]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalEvents.java#L1-L66
[M-ffi]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/Native.java#L1-L251
[M-frame-wait]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/FrameWait.java#L1-L14
[M-framegen]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/FrameGen.java#L1-L766
[M-fx]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalFx.java#L1-L132
[M-gpu-times]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/GpuTimes.java#L1-L134
[M-list-cache]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/cpu/ListRegionCacheMixin.java#L1-L32
[M-mesh-air]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/chunk/LevelSliceAirMixin.java#L1-L83
[M-mesh-biome]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/chunk/LevelBiomeSliceMixin.java#L1-L62
[M-mesh-block]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/chunk/BlockRendererMixin.java#L1-L70
[M-mesh-bounds]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/chunk/LevelSliceBoundsMixin.java#L1-L62
[M-mesh-vis]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/mixin/chunk/DirectionalVisGraphMixin.java#L1-L51
[M-native-clear]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/native/mcmetal.m#L669-L807
[M-native-draw]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/native/mcmetal.m#L809-L1026
[M-native-memory]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/native/mcmetal.m#L130-L292
[M-native-pass]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/native/mcmetal.m#L427-L477
[M-native-resources]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/native/mcmetal.m#L260-L426
[M-native-sleep]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/native/mcmetal.m#L1027-L1161
[M-native-submit]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/native/mcmetal.m#L427-L575
[M-native-terrain]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/native/mcterrain.m#L1-L458
[M-notice]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/NOTICE#L1-L28
[M-occ]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalOccPick.java#L1-L59
[M-pass]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalRenderPass.java#L1-L386
[M-pipeline]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalPipeline.java#L1-L363
[M-preload]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/Preload.java#L1-L58
[M-profile]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/Profile.java#L1-L147
[M-profile-alpha]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/resources/mcopt/profiles/alpha.properties#L1-L17
[M-properties]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/gradle.properties#L1-L9
[M-qos]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/Qos.java#L1-L85
[M-readme]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/README.md#L1-L56
[M-release]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/RELEASE-README.txt#L1-L37
[M-surface]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalSurface.java#L1-L91
[M-terrain]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalTerrain.java#L1-L528
[M-terrain-fs]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/resources/mcopt/metal/msl/sodium_terrain.fs.metal#L1-L69
[M-terrain-vs]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/resources/mcopt/metal/msl/sodium_terrain.vs.metal#L1-L110
[M-texel]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/TexelViews.java#L1-L62
[M-transient]: https://github.com/noahdunnagan/mcopt/blob/7fdeeea5845f22081308c17b4aca17eae2b6930e/metal/src/main/java/mcopt/metal/MetalTransientMemory.java#L1-L134
[S-new-collector]: https://github.com/CaffeineMC/sodium/blob/44b89f42dec873aa7c799224d7a10359b393a487/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/lists/VisibleChunkCollector.java#L1-L66
[S-new-context]: https://github.com/CaffeineMC/sodium/blob/44b89f42dec873aa7c799224d7a10359b393a487/common/src/main/java/net/caffeinemc/mods/sodium/client/gpu/device/context/GLDrawContext.java#L1-L26
[S-new-draw]: https://github.com/CaffeineMC/sodium/blob/44b89f42dec873aa7c799224d7a10359b393a487/common/src/main/java/net/caffeinemc/mods/sodium/client/gpu/device/batch/GLDrawBatch.java#L1-L46
[S-new-renderer]: https://github.com/CaffeineMC/sodium/blob/44b89f42dec873aa7c799224d7a10359b393a487/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.java#L1-L391
[S-old-biome]: https://github.com/CaffeineMC/sodium/blob/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413/common/src/main/java/net/caffeinemc/mods/sodium/client/world/biome/LevelBiomeSlice.java#L1-L256
[S-old-clones]: https://github.com/CaffeineMC/sodium/blob/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413/common/src/main/java/net/caffeinemc/mods/sodium/client/world/cloned/ClonedChunkSectionCache.java#L1-L79
[S-old-collector]: https://github.com/CaffeineMC/sodium/blob/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/lists/VisibleChunkCollector.java#L1-L66
[S-old-draw]: https://github.com/CaffeineMC/sodium/blob/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/DefaultChunkRenderer.java#L1-L367
[S-old-index]: https://github.com/CaffeineMC/sodium/blob/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/SharedQuadIndexBuffer.java#L1-L144
[S-old-mesher]: https://github.com/CaffeineMC/sodium/blob/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/compile/tasks/ChunkBuilderMeshingTask.java#L1-L300
[S-old-slice]: https://github.com/CaffeineMC/sodium/blob/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413/common/src/main/java/net/caffeinemc/mods/sodium/client/world/LevelSlice.java#L1-L404
[S-old-stage]: https://github.com/CaffeineMC/sodium/blob/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413/common/src/main/java/net/caffeinemc/mods/sodium/client/gl/arena/staging/MappedStagingBuffer.java#L1-L211
[S-old-times]: https://github.com/CaffeineMC/sodium/blob/eb81e48d2646f76e7dbd1337d0f6ecb6a6dc5413/common/src/main/java/net/caffeinemc/mods/sodium/client/render/chunk/UniformBufferManager.java#L1-L245
