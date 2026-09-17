# Settings architecture

## Framework and growth

eviemod uses Dandelion with the MoulConfig backend. The supplied Skyblocker 6.10.2+26.1.2 uses Dandelion for its main settings, with both MoulConfig and YACL backend support. Merely finding bundled YACL was insufficient to identify that architecture.

MoulConfig supplies a scrolling category sidebar, search and collapsible groups. Dandelion's standard public structure is **category → group → option**, with optional ungrouped options. This supports many feature categories and groups, but does not expose arbitrary recursive subcategories. If a future feature needs deeper nesting, extend the backend integration or add a dedicated custom panel; do not encode an unlimited tree assumption in feature configuration.

## Adding a feature

`SettingsCategories` holds a list of category factories. Add a factory there (large features can own separate classes), give categories/groups/options stable `eviemod:` identifiers, and bind options to a draft in `ModSettings.Values`. Use descriptive labels and search tags for terminology users may search. Keep groups focused on a feature. Persist defaults and validation in `ModSettings`, separate from rendering.

`EviemodSettings` creates the settings screen, adapts the validated atomic JSON store, and handles save errors. Ordinary settings save when the settings screen closes. The Paint Brush category uses Dandelion's standard `ButtonOption` to open a separate editor, as explicitly requested by the user. `/paintbrush` opens that editor directly.

## Paint Brush editor

`PaintBrushScreen` retains native Minecraft widget input, model/dye/name editing and the inventory picker. The editor returns to its originating settings screen on Done; opening the picker and switching tabs preserve drafts. Apply and Reset retain their existing per-tab persistence behavior. Closing asks before discarding unapplied edits.

`EditorTheme` isolates use of MoulConfig's public panel renderer so the editor and picker match the settings surfaces. Native widgets and the existing autocomplete/selection logic remain in use. There is no custom MoulConfig option or input-coordinate bridge for the editor.

## Dependency boundaries

The exact Minecraft 26.1 backport is pinned in `libs`; see its README for provenance. The settings store adapter extends `ConfigManagerImpl`, required by this version’s MoulConfig save wiring. Recheck it and the panel-rendering adapter when upgrading. YACL remains a bundled Dandelion dependency, not an alternative selectable eviemod backend.

`./gradlew build` checks parsing, identity, persistence and editor helpers. `./gradlew runClient -PuiSmokeTest -PuiCapture` exercises editor input, picker return and closing back to settings, and captures the settings/editor screens. The fixture maps editor coordinates through the same viewport transform and is excluded from release artifacts.
