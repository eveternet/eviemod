# Project principles

These instructions apply throughout eviemod.

## Anti-slop: intentional features, off by default

- We are anti-slop. Keep changes purposeful and focused on the requested behavior. Avoid feature creep, redundant controls, decorative clutter, filler copy, and speculative abstractions.
- Every new feature must default to off. Explicitly choosing or applying a per-item customization in the shared configuration UI counts as opting in; do not add a second enable toggle ("opt in to opt in"). Automatic/background features still require an explicit enable setting. This applies to fresh installs, newly added settings in existing configurations, and configuration resets; missing settings must resolve to off.
- Updates and migrations must never silently enable a new feature. Preserve the player's explicit choices for existing features.

## Versioning

- Use four numeric components: `important.new-feature.modification.patch` (for example, `1.0.0.0`). This is intentional; never normalize it to three-component SemVer.
- The first component marks important releases; the second adds a new feature; the third modifies a current feature; the fourth fixes bugs, debugging findings, or UX criticism, including corrections to prior implementations. A fix touching several files is still a patch.
- When incrementing a component, reset the components to its right to zero. Preserve the version unless a requested change calls for a bump; use an explicitly requested version exactly.
- Choose one version for the user-facing change; do not bump again for intermediate builds or test fixes.

## Release requests

- An explicit instruction to "cut a release" authorizes the full release process: build and test the intended commit, commit the task's changes, push the release commit, create and push its `v<version>` tag, and verify that GitHub Actions creates the GitHub Release with the distributable mod JAR attached.
- Use the four-component project version unchanged after the tag's `v` prefix. Never move or overwrite an existing release tag.
- Other requests, including building a local release, changing a version, committing, or pushing normal changes, do not authorize creating or pushing a release tag or publishing a GitHub Release. A local release means building the distributable JAR locally only. Follow any explicit restriction on publishing even when release terminology is used.

## Commit after every build

- After every build, commit any uncommitted changes made for the current task. Include the build outcome in the commit message; clearly label failed builds as work in progress.
- Review the staged diff before committing. Keep unrelated changes, generated build output, local game data, and secrets out of the commit.
- If there are no changes to commit, do not create an empty commit. Commit locally without asking for confirmation; push only when requested.

## Pull request review

- After opening a pull request, comment `@coderabbit review` on that PR to request CodeRabbit review.
- After triggering a review, estimate its processing time from the PR's size and depth, usually 5–15 minutes. Wait that long before checking CodeRabbit's status instead of polling repeatedly; a delayed wait such as `sleep 300` is fine when appropriate. Remain available for user messages while waiting.
- Check the review and its status checks. Address actionable findings and push fixes to the same PR. Request another review only when new commits have been pushed since the last completed review and those commits still need review; CodeRabbit does not re-review already reviewed commits. Respect its review limit, and do not retrigger a completed review just to clear advisory warnings. Explain any finding that cannot be resolved instead of silently dismissing it.

## Build on established UI

- Prefer existing, maintained UI frameworks and their standard controls, navigation, search, layout, and accessibility behavior. Avoid novel UI solutions when an established pattern meets the need.
- Extend the shared eviemod configuration UI instead of creating disconnected feature screens. Organize growing features into discoverable categories and focused groups.
- Reuse the project's Dandelion/MoulConfig integration and existing editor components. Introduce custom widgets or framework internals only when a concrete requirement cannot reasonably be met through supported APIs; keep those adaptations small and isolated.
- Check the actual reference implementation before claiming compatibility with another mod's UI or behavior. A bundled dependency alone does not establish how it is used.

## Minimize upgrade fragility

- Favor supported Minecraft, Fabric, and library APIs over implementation details. Use the smallest, most specific mixin hook needed; avoid broad interception of general item/component access.
- Isolate version-sensitive rendering, input, configuration-framework, and SkyBlock metadata access behind small adapters. Keep feature logic and persistence independent of those adapters so upgrades have a narrow impact.
- Prefer stable identifiers and structured data over display names, translated text, lore formatting, screen titles, or fixed coordinates. Where SkyBlock requires a heuristic, centralize it, document the evidence and assumptions, and handle missing or changed data gracefully.
- Preserve normal game behavior when data is unknown, malformed, or unsupported. Do not guess item identity or let a fallback apply beyond its verified ownership and rendering scope.
- Keep cosmetic changes local to rendering. Do not mutate live item components or server data to implement a visual feature.
- Preserve existing configuration and command compatibility unless a requested change requires otherwise. Use explicit defaults, validation, and safe migrations; never overwrite malformed user configuration silently.
- Avoid speculative abstractions and unnecessary dependencies. Prefer straightforward code with clear boundaries; document unavoidable internal APIs and pinned backports so future upgrades can find them easily.

## Verify and document assumptions

- Add focused regression coverage for meaningful behavior and fragile integration boundaries. On dependency or Minecraft upgrades, check mixin application, metadata parsing, persistence, and actual UI interaction as appropriate.
- Distinguish local fixture validation from live SkyBlock testing. Do not claim compatibility with future versions or server behavior without evidence.
- Keep technical limitations, workaround explanations, and version-specific findings in source Markdown. Do not expose developer diagnostics or bug documentation in the player-facing UI unless explicitly requested.
