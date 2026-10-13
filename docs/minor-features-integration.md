# Minor feature integration provenance

The integration starts at `origin/main` **8bd2dcafc8e17d2d3c6de8f01c2fb750b71936ef**,
which already supports the main mod on Minecraft 26.1.2 and 26.2 through merged
PR #16. It uses one version, **1.4.0.0**, chosen by both new feature PRs.

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
The combined branch receives a separate bounded, fresh Codex CLI review through
the local eviedex runner. Saved private logs identify its exact comparison SHAs,
coverage and any findings; they are kept outside this repository.

Use the [live-test checklist](live-test-minor-features.md) for behavior requiring
Hypixel and the intended modpack. No release/tag, game launch, modpack installation,
source PR merge/closure or credentials/security change is part of this task.
