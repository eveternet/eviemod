# Minecraft version builds

The main mod has separate Fabric artifacts for Minecraft 26.2 and 26.3. Install
exactly one matching the game version. Both keep the same mod ID, four-component
version, configuration files, commands, and opt-in settings.

Minecraft 26.1 support is retired. Its adapter sources, CI job, and vendored
Dandelion backport are removed; existing published 26.1 releases remain available.
The mod version is unchanged by this port; no release tag is created.

| Target | Project | Distributable |
| --- | --- | --- |
| Minecraft 26.3 | root | `build/libs/eviemod-<version>-mc26.3.jar` |
| Minecraft 26.2 | `:eviemod-26.2` | `eviemod-26.2/build/libs/eviemod-<version>-mc26.2.jar` |

`./gradlew build` builds and tests both targets. `:build` also depends on
`:eviemod-26.2:build`. Java 25 and Gradle 9.6.0 are used for both targets.

## Dependencies and source boundaries

| Dependency | 26.3 | 26.2 |
| --- | --- | --- |
| Fabric API | `0.167.0+26.3` | `0.155.2+26.2` |
| Fabric Loader | `0.20.1` | `0.19.5` |
| YACL | `3.9.7+26.3-fabric` | `3.9.4+26.2-fabric` |
| Dandelion | `1.0.0-alpha.23+26.3` | `1.0.0-alpha.22+26.2` |
| Hypixel Mod API | `1.0.5+26.3` | `1.0.4+26.2` |
| Mod Menu (compile only) | `21.0.1` | `19.0.0-alpha.1` |
| Fabric Language Kotlin | `1.14.2+kotlin.2.4.21` | `1.14.1+kotlin.2.4.20` |

`gradle/eviemod.gradle` shares feature sources and unit tests. Version-sensitive
input, desktop file selection, GUI/HUD access, chunk refresh, fog, and equip
animation hooks live in `src/<minecraft-version>/java`. Each artifact includes
one implementation per boundary. Metadata and third-party notices expand to the
actual dependencies. Offline-only registry/input fixtures live in each target's
`testFixtures` directory and are excluded from distributable JARs.

## 26.3 port boundaries

- SDL replaces GLFW. UI event comparisons use vanilla `InputConstants`, including
  mouse buttons. The command-hotkey integer format remains unchanged:
  `LegacyHotkeyCodes` maps the 26.2 vanilla key identifiers to the current game's
  input codes for display, capture, and polling. Existing keyboard/mouse bindings
  retain their meanings across both targets. Unsupported legacy keys remain saved
  but inactive; unrecognized new keys cannot be captured. Garden bindings already
  persist vanilla key identifiers. No migration silently enables or resets a feature.
- Editors use vanilla `EditBox` focus notifications for SDL text input. Fixture
  key events carry SDL keycodes as well as scancodes, exercising text editing and
  navigation rather than only direct method calls with old GLFW integers.
- SDL's file picker runs asynchronously from the client thread, retaining native
  filter/callback memory until completion. Selected files return to the client
  thread; closed editors and changed item drafts ignore late selections. The 26.2
  adapter retains TinyFileDialogs. Native OS picker interaction still needs manual
  testing; the offline fixture does not automate an operating-system dialog.
- Equip state moved from `ItemInHandRenderer` to
  `LocalPlayer.firstPersonHandsAndItems()`. The mixin targets
  `FirstPersonHandsAndItems`, and the attack hook uses 26.3's new swing descriptor.
  Bytecode tests check the reset/swing order and swap-scale boundary for each game.
- Lava fog base color now returns `Vector3fc` instead of packed ARGB. Each target
  has a typed mixin, while options and tint persistence stay shared.
- Both versions own HUD operations in `Hud` and chunk invalidation in
  `LevelExtractor`. Imported pack metadata uses the running resource-pack format.

References checked for the port:

- [Fabric 26.3 migration overview](https://fabricmc.net/2026/09/15/263.html)
- [Mojang version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- [Dandelion published versions](https://maven.azureaaron.net/releases/net/azureaaron/dandelion/maven-metadata.xml)
- [SDL file-dialog lifetime and threading](https://wiki.libsdl.org/SDL3/SDL_ShowOpenFileDialog)
- Actual 26.2 and 26.3 client bytecode from Loom's resolved Minecraft dependencies.

## CI and validation

CI builds/tests both targets, checks artifact separation and bundled dependencies,
and launches both clients with `-PuiSmokeTest -PversionSmokeTest`. The fixture forces
required mixin owners to load and opens, edits, renders, and closes the shared
settings, Paint Brush, and command-hotkey screens. It also checks equip-animation
configuration persistence and the transformed use hook.

CI uploads `eviemod-mc26.3` and `eviemod-mc26.2` separately. Tagged releases attach
both JARs, with no 26.1 artifact. Local dual builds, fixture compilation, all 456
unit tests, and artifact validation passed for this port.

Live Hypixel behavior, third-party modpack interactions, and native file-picker
interaction require separate testing. Offline fixtures do not establish those.
