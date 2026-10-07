# eviemod

A client-side Fabric mod for Hypixel SkyBlock on Minecraft 26.1.2 and 26.2, with item customization and optional quality-of-life features. Requires Java 25 and Fabric API. Configure it with `/eviemod`; features default to off until enabled or applied. Licensed under [MIT](LICENSE); bundled third-party components retain their own licenses.

- **Paint Brush:** per-item models, static and animated PNG imports, helmet skins, custom and animated dyes, and styled names with colors and gradients.
- **Rarity backgrounds:** customizable shapes and opacity, with optional rarity continuity during item-data refreshes.
- **Hypixel Pack:** local installation and updates for the official SkyBlock resource pack.
- **SkyBlock visuals:** hide barrier effects, scale health to 10 hearts outside the Rift, and prevent repeated Soul Whip equip animations.
- **Garden tools:** farming mouse lock and hotkeys for plot teleport, set spawn, and Garden warp.
- **Dungeons:** Noamm score sync and customizable Explosive Shot damage announcements, locally or in party chat.
- **Chat commands:** warp, coordinates, invite, kick, party transfer, ping, TPS, and FPS, individually configurable for party, guild, and co-op chat.
- **Command hotkeys:** custom key bindings for commands.

An optional [experimental Apple Silicon Metal addon](docs/metal-addon.md) is
built as a separate `eviemod-metal` JAR. Installing it enables Metal on supported
Macs without a custom JVM argument. It is not bundled with the main mod.

`./gradlew build` builds both game targets. Use the JAR matching your Minecraft
version: `build/libs/eviemod-<version>-mc26.1.2.jar` or
`eviemod-26.2/build/libs/eviemod-<version>-mc26.2.jar`. CI uploads both separately,
and tagged releases attach both. The standalone Metal addon still targets 26.1.2.
See [the version build notes](docs/minecraft-versions.md) for dependencies and port validation.
