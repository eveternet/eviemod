# Project principles

These instructions apply throughout eviemod.

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
