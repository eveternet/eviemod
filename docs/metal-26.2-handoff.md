# Eviemetal 26.2 handoff

This draft PR hands the current work to a cloud agent at the user's request on 2026-10-07.
Continue branch `codex/eviemetal-dual-minecraft`. It starts from PR 16's head
`b1ae1b3fc40f73462becfa166fd4aaa5ce598ddf`; the PR is stacked on
`codex/dual-minecraft-builds`, not `main`. Keep PR 16's main-mod 26.2 work.
Do not fork the shared native bridge or replace the retained 26.1.2 adapter.

## Implemented and locally passing

Normal root builds produce both versioned `0.2.0.0` addon JARs. The 26.2 startup,
surface, resource/pipeline/pass adapters and pinned Sodium draw context work on
a physical Apple M3 Pro. Local tests pass: 48 retained addon tests, 42 new adapter
tests and 172 main-mod tests for each target. Packaged vanilla and Sodium worlds
have completed the lifecycle route on both Minecraft versions. Real Sodium arena
resize/relocation/recycling, timestamp resizing and world-owned buffer closure
checks pass. Forced Vulkan preference with explicit Metal disable selects Vulkan;
a preflight fault cleans native state before a successful OpenGL retry.

Read [the port report](metal-26.2-port.md) for exact evidence, before/after baseline,
source boundaries, limitations and local logs. Read `AGENTS.md` for build/commit,
versioning and CI-before-CodeRabbit rules. Addon version stays `0.2.0.0`; the main
mod stays `1.3.1.0`. No release is authorized by this handoff.

## Remaining work, in priority order

- [ ] Run and inspect **all applicable GitHub CI** on the latest pushed revision.
  CI changes have not yet been exercised remotely. Fix failures on this branch,
  rerun and wait for successful latest-revision checks. Linux tests must actually
  exercise both packaged unsupported-host/disable routes. Mac jobs must verify
  native distributions and both vanilla/Sodium lifecycle targets. Watch hosted
  paravirtual GPU timestamp support and graphical lifecycle behavior; report any
  runner-only limitation separately from physical Apple Silicon evidence.
- [ ] Repeat **complete packaged 26.2 vanilla and Sodium lifecycles** on the final
  revision with the stricter launch classpath that removes loose shader-tool JARs.
  That classpath already passed 26.2 title/retry checks and both 26.1.2 world routes.
  The last full 26.2 worlds passed before that tightening and the final startup
  helper refactor. Confirm nested tool code-source assertions, stress markers,
  both world unload ownership checks and clean shutdown together.
- [ ] Repeat completed GPU memory measurements for vanilla 26.2 with controlled
  window/framebuffer dimensions. Initial final allocation was 663,666,688 bytes;
  Sodium repetitions were 246,841,344 and 226,525,184. Do not label the difference
  a leak or a passing bound without comparable repetitions. Record dimensions,
  completed fences and resource ownership, rather than sampling pending GPU work.
- [ ] Preserve explicit **title-screen screenshot coverage in CI**, including with
  Sodium. The current Mac workflow drives world fixtures that pass through title,
  but does not invoke `packagedTitleSmoke`. Add a pinned Sodium title-only flavor
  if useful; its screenshot must actually render and close. Existing standalone
  title evidence is local. Run graphical fixtures serially on one display.
- [ ] Finish the packaged compatibility matrix: explicit disable **with Sodium
  installed**, unsupported Sodium rejection through an actual packaged launch,
  and preflight retry preserving both vanilla backend preferences where supported.
  Policy/version and actual startup/factory-anchor tests pass; physical disable
  already verified Vulkan and vanilla preflight retry verified OpenGL. Linux CI
  supplies real unsupported-host checks. Hosted Mac OpenGL pixel-format availability
  may differ from a physical Mac.
- [ ] Review the **complete final implementation diff** against actual 26.2 and
  Sodium sources, then review the actual PR changed lines. Per-commit staged diffs
  have been checked, but the final whole-port review remains unfinished. Confirm
  cache/layout identity, advertised limits, buffer range/offset validation, cleanup
  after failures, timestamp encoder restoration and native surface state. Keep
  unsupported forms explicit; do not silently emulate or expand renderer scope.
- [ ] Request `@coderabbit review` only after applicable latest-revision CI passes.
  No review request has been made for this port. Allow approximately 10–15 minutes
  for this large diff, inspect findings/checks, fix actionable issues on this branch
  and satisfy CI again before a follow-up review. Respect review limits.
- [ ] Update this checklist, port evidence and PR description with final outcomes.
  Mark the PR ready only when the original completion criteria are met: both
  packages/root build, retained baseline tests, 26.2 vanilla/Sodium packaged
  lifecycles, native distribution checks and passing CI/review.

No live SkyBlock validation is required to make implementation progress; none is
claimed. Do not add release tags or publish a release while completing the PR.

## Reproduction

On arm64 macOS with Java 25 and Xcode tools:

```sh
MTL_DEBUG_LAYER=1 ./gradlew build verifyMetalDistributions
python3 .github/scripts/verify-metal-artifacts.py
MTL_DEBUG_LAYER=1 ./gradlew :metal-addon-26.2:packagedSurfaceSmoke
MTL_DEBUG_LAYER=1 ./gradlew :metal-addon-26.2:packagedTitleSmoke
MTL_DEBUG_LAYER=1 ./gradlew :metal-addon-26.2:packagedVanillaSmoke
MTL_DEBUG_LAYER=1 ./gradlew :metal-addon-26.2:packagedSodiumSmoke
MTL_DEBUG_LAYER=1 ./gradlew :metal-addon:packagedVanillaSmoke
MTL_DEBUG_LAYER=1 ./gradlew :metal-addon:packagedSodiumSmoke
```

Linux can build/test the Java adapters, check artifact separation and run the
packaged fallback/disable fixtures under Xvfb. Linux's Java-only JARs intentionally
fail native distribution verification; publish only the Mac distributions.
`verifyPackagedMetal` aggregates both adapters' surface/world fixtures. Fixtures
stage version-specific JARs and exact Sodium in `<module>/build/packagedSmoke/`;
they do not include addon implementation classes in the client classpath.

The original 26.2 primer and authoritative upstream links are in the port report.
Local decompiled/source checkouts under `/tmp/` will not be available to a cloud
agent: resolve Loom's exact Minecraft 26.2 client and the published Sodium
`mc26.2-0.9.2` tag, commit `6c26e7b7eded82ce5a1d27f9b147ce5d8de99b7a`, again.
