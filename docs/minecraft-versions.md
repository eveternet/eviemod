# Minecraft version builds

The main mod has two separately compiled Fabric artifacts. Install exactly one,
matching the game version. Their Fabric mod ID and four-component eviemod version
remain identical, so configuration files and commands are shared across targets.

| Target | Project | Distributable |
| --- | --- | --- |
| Minecraft 26.1.2 | root | `build/libs/eviemod-<version>-mc26.1.2.jar` |
| Minecraft 26.2 | `:eviemod-26.2` | `eviemod-26.2/build/libs/eviemod-<version>-mc26.2.jar` |
| Standalone Metal addon, Minecraft 26.1.2 | `:metal-addon` | `metal-addon/build/libs/eviemod-metal-<addon-version>.jar` |

`./gradlew build` builds and tests both main-mod targets and the existing addon.
`:build` also depends on `:eviemod-26.2:build`. The addon is independent of both
main-mod artifacts and has not been ported to the 26.2 rendering backend interfaces.
Its native distribution still requires building on macOS.

## Dependencies and source boundaries

| Dependency | 26.1.2 | 26.2 |
| --- | --- | --- |
| Fabric API | `0.155.2+26.1.2` | `0.155.2+26.2` |
| Fabric Loader | `0.19.5` | `0.19.5` |
| YACL | `3.9.2+26.1-fabric` | `3.9.4+26.2-fabric` |
| Dandelion | vendored `1.0.0-alpha.21+26.1` backport | upstream `1.0.0-alpha.22+26.2` |
| Hypixel Mod API | `1.0.3+26.1` | `1.0.4+26.2` |
| Mod Menu (compile only) | `18.0.1` | `19.0.0-alpha.1` |
| Java | 25 | 25 |

`gradle/eviemod.gradle` holds the shared build configuration. Both projects compile
`src/main/java` and run the same `src/test/java` tests. Only GUI/HUD access and the
two HUD mixins come from `src/<minecraft-version>/java`; each artifact contains
one implementation of each adapter. Resource processing writes exact Minecraft
and target-specific dependency requirements into `fabric.mod.json`.

26.2 moves screen and overlay ownership to `Gui`, toast access to `Gui`, and
in-game chat/title/health/hotbar operations to `Hud`. `ClientUi` isolates the
call-site changes, and the 26.2 HUD mixins target `Hud` directly. Shared logic does
not use reflection to guess which vanilla API exists. Text-color test fixtures
use `Style` instead of the removed legacy color constants. Imported texture
pack metadata uses the running game's resource-pack format.

Porting references:

- [Fabric 26.2 migration overview](https://fabricmc.net/2026/06/15/262.html)
- [26.1.x to 26.2 migration primer](https://github.com/neoforged/.github/blob/main/primers/26.2/index.md)
- [Dandelion 26.2 sources](https://github.com/AzureAaron/Dandelion/tree/f3473b8)
- [Fabric API 0.155.2+26.2](https://github.com/FabricMC/fabric-api/tree/0.155.2%2B26.2)

## CI and validation

The Linux build compiles and tests both main-mod targets, checks artifact
separation and bundled Dandelion versions, and launches each game target with
`-PuiSmokeTest -PversionSmokeTest`. The offline fixture forces required mixin
owners to load and opens, renders, and closes the actual shared settings and
Paint Brush screens, checking return navigation. These fixture mods are not
included in distributable JARs.

CI uploads `eviemod-mc26.1.2` and `eviemod-mc26.2` separately. Tagged releases
download both and attach the two main-mod JARs plus the standalone 26.1.2 Metal
addon. Existing Metal fallback, native-frame, Sodium terrain and lifecycle
checks remain in place.

Live Hypixel behavior and third-party modpack interaction require separate game
testing; offline fixtures do not establish those. Initial local Gradle execution
was blocked because the environment could not download Gradle or Java 25.
Compilation and launch results are recorded by the PR's CI checks.
