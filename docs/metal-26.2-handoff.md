# Eviemetal 26.2 continuation record

The initial cloud handoff is superseded by the implementation and validation
record in [the port report](metal-26.2-port.md). Continue or review
`codex/eviemetal-dual-minecraft`, [PR 17](https://github.com/eveternet/eviemod/pull/17).
It starts from PR 16's head `b1ae1b3fc40f73462becfa166fd4aaa5ce598ddf` and remains
stacked on `codex/dual-minecraft-builds`, preserving that branch's main-mod port.

## Handoff checklist outcome

- Both fixed Minecraft adapters resolve independently and a normal root build
  produces both versioned addon JARs. One native bridge and shared Metal utilities
  serve both. Addon version is `0.2.0.0`; main-mod version remains `1.3.1.0`.
- Fresh physical-Mac tests pass: 48 retained addon tests, 50 new adapter tests and
  172 main-mod tests for each target, with zero failures, errors or skips.
- Complete packaged vanilla and pinned Sodium title/world routes pass on both
  versions under Metal validation, using nested shader-tool dependencies. Final
  26.2 runs include actual arena growth, relocation, recycling, timestamp resize,
  both world unload ownership checks, and native close verification.
- Comparable completed-GPU memory repetitions now record a controlled framebuffer,
  completed fences and device identity. The port report records the measurements
  and their limits; it does not claim a long-duration soak or performance gain.
- CI has explicit vanilla and pinned Sodium title screenshots for each addon.
  Graphical tasks run serially on one display, including aggregate invocations.
- The packaged compatibility matrix checks explicit disable with Sodium,
  unsupported-host behavior, unsupported Sodium rejection, and native preflight
  cleanup followed by vanilla OpenGL/Vulkan retry with the original preference.
- The complete implementation diff was inspected against actual Minecraft 26.2
  and published Sodium sources. The retained adapter's Java renderer remains
  unchanged. Final review corrected depth-format partial-clear caching and valid
  zero-byte arena copies in the shared native implementation, with pixel/native
  regression coverage in the relevant adapters.
- Applicable implementation CI passed before CodeRabbit review was requested.
  Both initial findings were corrected in `49fc43f`: regional clear bounds,
  closed-target checks and empty-region behavior now have pixel coverage, and
  macOS checkout no longer persists credentials. Its root build passes all 442
  tests and its packaged 26.2 surface/title/world/compatibility routes pass again.
  Latest-revision CI and the follow-up review are tracked on PR 17.
  Follow `AGENTS.md` for the latest-revision CI gate before any further code review.

The hosted Apple Paravirtual GPU does not expose Minecraft 26.2's required
stage-boundary timestamp counters. Hosted 26.2 title/world routes are explicitly
**not validated** there; they pass on the physical Apple M3 Pro. This limitation
must remain visible even when hosted CI is green. The independent surface,
compatibility routes, native distributions and remaining tests run in CI.

Known unsupported rendering forms and installation/activation behavior are in
[the addon guide](metal-addon.md). No live SkyBlock/full-modpack validation is
claimed. No release or tag is authorized or created by completing this PR.

## Reproduction

On timestamp-capable Apple Silicon macOS with Java 25 and Xcode tools:

```sh
MTL_DEBUG_LAYER=1 ./gradlew build verifyMetalDistributions
python3 .github/scripts/report-metal-tests.py
python3 .github/scripts/verify-metal-artifacts.py
MTL_DEBUG_LAYER=1 ./gradlew verifyPackagedMetal
MTL_DEBUG_LAYER=1 python3 .github/scripts/verify-metal-compatibility.py
```

For strict, individually reported graphical routes:

```sh
MTL_DEBUG_LAYER=1 ./gradlew :metal-addon-26.2:packagedSurfaceSmoke
MTL_DEBUG_LAYER=1 python3 .github/scripts/verify-metal-fixture.py metal-addon-26.2 Title
MTL_DEBUG_LAYER=1 python3 .github/scripts/verify-metal-fixture.py metal-addon-26.2 SodiumTitle
MTL_DEBUG_LAYER=1 python3 .github/scripts/verify-metal-fixture.py metal-addon-26.2 Vanilla
MTL_DEBUG_LAYER=1 python3 .github/scripts/verify-metal-fixture.py metal-addon-26.2 Sodium
MTL_DEBUG_LAYER=1 python3 .github/scripts/verify-metal-fixture.py metal-addon Vanilla
MTL_DEBUG_LAYER=1 python3 .github/scripts/verify-metal-fixture.py metal-addon Sodium
```

Linux can build/test the Java adapters, check artifact separation and run the
packaged fallback/disable fixtures under Xvfb. Linux's Java-only JARs intentionally
fail native distribution verification; distribute only the Mac packages.
Fixtures stage version-specific JARs and exact Sodium in
`<module>/build/packagedSmoke/`; they do not put addon implementation classes or
loose shader-tool classes on the client classpath. Generated worlds/logs/captures
remain outside source control.

The primer and authoritative upstream references are in the port report. Resolve
Loom's exact Minecraft 26.2 sources with `:metal-addon-26.2:genSources`, then run
`.github/scripts/report-metal-upstream.py`. Sodium is the published
`mc26.2-0.9.2` tag, commit `6c26e7b7eded82ce5a1d27f9b147ce5d8de99b7a`.
Local `/tmp/` evidence is not available to another agent; CI retains its own logs,
source-contract reports, test results and screenshots as artifacts.
