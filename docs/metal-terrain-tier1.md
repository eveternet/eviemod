# Sodium terrain Tier-1 changes

Implements PR [#12](https://github.com/eveternet/eviemod/pull/12)'s T1.1/D2 and T1.2/D3 against authoritative main `100806d`.

## T1.1 — Persistent structural layouts

`TerrainTessellation` snapshots the immutable attribute layout at construction.
A render-thread pool canonicalizes structurally equal layouts across regions.
Layouts cache their structural hash; an unchanged batch constructs no streams,
attribute records, attribute lists or composite PSO keys.

Each program indexes PSOs by a pass key (pipeline/color/depth, constructed once
when beginning a logical terrain pass), then by canonical layout. Both tessellations
and cached program variants retain the immutable layout, so replacing a region does
not lose canonical identity while its PSO remains cached. Tessellation deletion and
program deletion release those references. Device cleanup clears the pool.
Geometry buffer identities and storage still resolve at draw time.

## T1.2 — Numeric binding plans and safe deduplication

Program linking builds stage arrays containing native buffer/texture/sampler slots.
Uniform-block and sampler-unit setters update their numeric source points. Region
batches visit these arrays without traversing shader reflection maps or resolving names.

Binding-cache invalidation is deliberately conservative:

| Change | Invalidation |
| --- | --- |
| Logical begin/suspend/resume/end; intervening ordinary passes | Discard all cached slots, even if native encoders merge |
| Native encoder replacement | Monotonic native generation checked once per batch; pipeline and all slots replay |
| Program switch/delete | Discard slots and bound PSO |
| Buffer replacement/orphan/offset change | Compare Java resource identity, native handle, offset and storage generation; validate open state before skipping |
| Default uniform write | Per-stage content generation changes; pointer equality never suppresses dirty `setBytes` |
| Texture/view/sampler replacement | Resource identity and unit generation; closed resources fail validation |
| Section-time storage change | Refresh retained view before binding; track storage generation separately for each unit |
| Buffer versus bytes in the same slot; shared sampler slots | Track binding kind and actual stage/slot sampler state |
| Reflected slots outside the bounded cache | Use the existing direct native setter and discard cached state, including any shared sampler slot it may overwrite |

Storage generations are observation metadata for existing orphan allocation. This
does not implement T1.3 storage reuse, GPU-use tracking, renaming pools or allocation
policy changes. Native resources retain the existing command-buffer ownership.
Binding/compilation failures release logical pass ownership before rethrowing, so
a caller can recover and resume the configured target. Pass close invalidates cached
state in a `finally` block, including when cleanup itself fails.

## Validation and diagnostics

Platform-independent tests exercise canonical identity, every structural layout
field, 10,000 repeated PSO lookups without new snapshots, program/tessellation
ownership, cleanup/recreation, and reflection plans that continue working after
their input maps reject further traversal. A fake native sink verifies 40,000
binding attempts become four calls when unchanged; tests also cover dirty bytes,
resource/storage/offset changes, encoder/program/resume invalidation, stage isolation,
shared sampler slots, zero-sampler semantics, direct-binding fallback for unusual
slots and retry after a failing setter.

macOS native tests distinguish logical encoder merges from upload splits and assert
storage generations advance on existing timestamp orphan writes, alongside the
existing signed-time/offset/base-vertex pixel readback and memory plateau checks.
CI runs the existing build, artifact/fallback launch checks, macOS distribution and
frame readback checks, plus the existing Sodium world fixture. The fixture exercises
rebuilds, reloads, resize/fullscreen, GUI drawing, dimensions, unload and reopen;
its final counters must show repeated layout use and skipped bindings.

Counters are off by default. Enable `-Deviemod-metal.terrainProfile=true` (development
Gradle launches: `-PterrainProfile`) and read `SodiumMetal.performanceSummary()` or
`performanceCounters()` at phase boundaries. Counters are cumulative for the process,
bounded scalars with no retained history, per-draw logging or file IO.
`resourceSummary()` includes canonical-layout count; world cleanup asserts it is zero.

CI fixtures establish integration and operation counts, not FPS improvements,
physical-Mac performance or live Hypixel/modpack compatibility. SkyHanni, SBPV,
Devonian/Talium screens and a matched JFR/Metal trace still need physical testing.
Compare allocation stacks and counters with profiling disabled for throughput runs.

### Cloud evidence, 2026-10-05

Implementation `931b39f` passed [Linux and macOS CI](https://github.com/eveternet/eviemod/actions/runs/37328833564),
including the full build, artifact/OpenGL fallback checks, all 48 Metal tests
(no failures, errors or skips), native distribution and Metal frame readback.
The Sodium fixture recorded 147 layout snapshots, 34,014 batches and 19 PSO misses.
Of 238,098 binding attempts, 78,932 reached native setters: 159,166 were skipped
(66.8%). This counts binding setters; the encoder-generation query adds one JNI
call per batch. Default uniform copies still totalled 1,088,448 bytes (32 per batch).
Final unload reported zero Sodium buffers, borrowed buffers, programs, PSOs,
time views and layouts.

The runner reports **Apple Paravirtual device**, rather than a physical Apple GPU.
Screenshot inspection found most terrain absent while entities and inventory render.
A [separate baseline run](https://github.com/eveternet/eviemod/actions/runs/37330452585)
using original `100806d` production code and the same fixture reproduced that failure
in the initial/final overworld and End captures. Only the fixture's fresh-profile
onboarding handoff and diagnostic workflow differed from baseline. The cause is
unresolved; this does not establish that the failure is limited to virtual GPUs.
Lifecycle/counter markers and the native pixel tests therefore do **not** establish
full terrain image correctness or visual equivalence. Investigate that existing
rendering failure separately and validate terrain on physical Apple Silicon before
claiming visual compatibility or FPS gains from these changes.

T1.3 and all Tier-2 work are excluded. T1.4 is deferred without measured first-use
format/compile data. T1.5 is left separate to keep this PR about terrain setup.
No user-facing feature, default or dependency version changes. The release is
eviemod `1.3.1.0` with Metal addon `0.1.1.0`. The user approved release after live
testing; this does not resolve the separate CI rendering limitation described above.
