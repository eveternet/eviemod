# Minor feature integration provenance

The integration starts at `origin/main` **8bd2dcafc8e17d2d3c6de8f01c2fb750b71936ef**,
which already supports the main mod on Minecraft 26.1.2 and 26.2 through merged
PR #16. It uses one version, **1.4.0.0**, chosen by both new feature PRs.

The [live-test follow-up](minor-features-live-followup.md) advances the correction
build to **1.4.0.1**, retaining this inclusion/exclusion provenance. Historical
validation below describes the first combined build; current validation is
recorded in the follow-up evidence and PR.

| Open PR | Source head | Inclusion |
| --- | --- | --- |
| #18 Quick Navigation | `093e7665ae7dca78ef851d6ae63ddd477d1e0662` | Feature diff from #17 head only |
| #19 Wishing Compass | `e53459a97e9054c209993f6221b4582098bd80f5` | Feature diff from #18 head only |
| #20 Experimentation Table | `c1fd6f6603a408c440f4a6d08298b2d9aff3c826` | Feature diff from #18 head only |
| #17 Eviemetal dual version | `d9ced1396118c5e334c904979737e018b9dbb823` | Excluded by request |
| #12 Eviemetal performance roadmap | `55cc0d045dc0bffd368ef3d71ed2fc7ff800f950` | Excluded: documentation, no gameplay feature |

Actual stack: #17 → #18 → {#19, #20}. Merging source branch ancestry would import
#17. Instead, three aggregate feature patches were transplanted onto main.
The main-mod shared sources, adapters and tests have no changes between main
and #17's head, so the features need no excluded renderer implementation.
The patches applied cleanly with three-way application; no semantic conflict
required broadening scope. Source commits remain on their original branches;
this branch records their net feature changes rather than importing their ancestry.

The source feature patches' artifact-checker edits were adapted to main's checker:
both main JARs must have version 1.4.0.0 and all three feature entry points/mixins,
and exclude smoke-fixture classes and Eviemetal. Main's existing 26.1.2 addon
remains unchanged; the integration neither adds the 26.2 addon nor copies #17's
Metal build/CI changes. The source feature documentation's dual-addon validation
describes those original stacked branches, not this integration's retained addon.

Shared settings factories register Quick Navigation, Mining and Experiments;
client initialization registers each feature once. Compass observes particle/footer
packets; experiments observe container-content/slot packets. Their tail hooks act
on distinct packet methods. Navigation input uses Fabric screen events outside
inventory slots; experiment protection hooks manual slot clicks. Existing rendering
and input owners remain in place. Added regression coverage verifies legacy
migration/defaults, navigation-editor/parent saves preserving both other feature
choices, isolated resets and a full default-off reset. The offline version fixture
exercises experiment settings alongside compass and navigation and CI requires
all three completion markers for both Minecraft targets.

The independent #19 recheck at the source head was reported clean, with all five
findings closed, 34 focused tests across both versions and 96 independent assertions.
The initial combined branch received a separate bounded Codex CLI review through
the local eviedex runner before the later macOS protection alert. The live-feedback
follow-up uses supported independent reviewer tasks; the blocked executable is
not restored, invoked or bypassed. Saved private logs identify each review's exact
comparison SHAs, coverage and findings; they are kept outside this repository.

The initial integration review inspected available content from all 53 changed
files and reported two P2 findings: counted/explicit-none effect sections could
override a loading/truncation marker, and the older merged-feature fixture still
expected seven categories. Both were reproduced from source; the compass
regression failed on both Minecraft targets before the fix. Incomplete disclosure
now rejects absence before either count/no-effects return while preserving
independent list data and positive scent evidence. The old fixture expects all
nine categories, and the CI version fixture checks the same real category order.
A fresh bounded recheck covers these corrections and their regression coverage.
The first review was explicitly partial because eviedex redacted an unrelated
credential-like expression in the existing workflow. Its six changed completion
checks were inspected separately and both actual CI workflows passed at the
initial integration revision; latest-head CI is required again after corrections.

Local validation at the initial revision: full root build, retained addon native
distribution verification and both offline UI fixture compilations passed;
556 tests passed with no failures/errors/skips (254 per main target and 48
retained addon tests). The initial root build hit main's existing Xcode/Command
Line Tools SDK mismatch. An invocation-local `SDKROOT` pointing at the active
Xcode SDK resolved it without modifying addon files or system configuration.
The corrected revision passes the full build, native distribution check and both
UI fixture compilations, with 558 tests and no failures/errors/skips (255 per main
target and 48 retained addon tests). Its private evidence/deliverable manifest
records exact SHAs and JAR checksums.

Use the [live-test checklist](live-test-minor-features.md) for behavior requiring
Hypixel and the intended modpack. No release/tag, game launch, modpack installation,
source PR merge/closure or credentials/security change is part of this task.
