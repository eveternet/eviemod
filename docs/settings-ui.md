# Settings architecture

## Framework and growth

eviemod uses Dandelion with the MoulConfig backend. The supplied Skyblocker 6.10.2+26.1.2 uses Dandelion for its main settings, with both MoulConfig and YACL backend support. Merely finding bundled YACL was insufficient to identify that architecture.

MoulConfig supplies a scrolling category sidebar, search and collapsible groups. Dandelion's standard public structure is **category → group → option**, with optional ungrouped options. This supports many feature categories and groups, but does not expose arbitrary recursive subcategories. If a future feature needs deeper nesting, extend the backend integration or add a dedicated custom panel; do not encode an unlimited tree assumption in feature configuration.

## Adding a feature

`SettingsCategories` holds a list of category factories. Add a factory there (large features can own separate classes), give categories/groups/options stable `eviemod:` identifiers, and bind options to a draft in `ModSettings.Values`. Use descriptive labels and search tags for terminology users may search. Keep groups focused on a feature. Persist defaults and validation in `ModSettings`, separate from rendering.

`EviemodSettings` creates the screen, adapts the validated atomic JSON store, and handles save errors. Ordinary settings save when the screen closes. All entry points use that shell: the eviemod command, Mod Menu and the paintbrush shortcut. The latter starts with a search filter, which users can clear.

## Rich Paint Brush editor

`PaintBrushController` mounts the existing model/dye/name editor as a custom MoulConfig option. It translates rendering coordinates and mouse/keyboard events and lets the shell handle Escape unless an autocomplete list consumes it. The item picker returns to the same shell. One editor instance owns drafts for the lifetime of the settings screen, including category changes and picker visits. Apply and Reset retain their existing per-tab persistence behavior. Closing the shell asks before discarding unapplied item edits.

## Dependency boundaries

The exact Minecraft 26.1 backport is pinned in `libs`; see its README for provenance. This adapter uses two implementation-level boundaries: `ConfigManagerImpl` (required by this Dandelion version's MoulConfig save wiring) and custom MoulConfig option rendering/input. Recheck those when upgrading; the custom editor currently targets MoulConfig only. YACL remains a bundled Dandelion dependency, not an alternative selectable eviemod backend.

`./gradlew build` checks parsing, identity, persistence and editor helpers. `./gradlew runClient -PuiSmokeTest -PuiCapture` exercises the actual backend's input routing and captures the settings/editor screens. The fixture uses fixed coordinates for the development GUI size and is excluded from release artifacts.
