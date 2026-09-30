# Rift detection discovery

Audited `main` at commit [`33b8e41c7f74f8288a56b339eebea0d7cce7a81d`](https://github.com/eveternet/eviemod/commit/33b8e41c7f74f8288a56b339eebea0d7cce7a81d), project version `1.1.1.0`. This document describes the existing implementation; it does not change detection, settings, or health behavior. Pending branches and pull requests are outside this snapshot.

## Finding

Eviemod infers Rift presence from text in the world's scoreboard. `SkyblockContext` stores a private, cached `rift` boolean, set when any scanned text contains one of ten phrases after formatting removal and uppercasing. It does not use a structured Rift location identifier.

The only consumer of that boolean is `SkyblockContext.shouldNormalizeHealth()`: Rift detection prevents the optional **Max 10 Hearts** feature from normalizing health. There is no public `isInRift()` accessor or separate Rift feature toggle.

## Inputs and scan behavior

Before scanning, `tick()` and `refreshNow()` check the current server address and require a non-null client world. Address normalization trims whitespace, lowercases with `Locale.ROOT`, removes everything from the first `:` onward, and removes trailing dots. The accepted host is exactly `hypixel.net` or a host ending in `.hypixel.net`; `evilhypixel.net` does not match. This is an address-string check, not verification of the resolved server's identity. A direct IP address or another alias does not qualify.

`refreshScoreboardState()` initializes both `foundSkyblock` and `foundRift` to false, then checks:

| Source | Text examined | Detail |
| --- | --- | --- |
| `DisplaySlot.SIDEBAR` objective | Objective display name | A missing sidebar skips this part; teams are still scanned. |
| Every score entry returned for that objective | `entry.display()`, if non-null | This takes precedence over the entry owner and its team in the entry reader. There is no additional hidden-entry or visible-row filter. |
| A score entry without an explicit display component | Entry owner, plus its team's player prefix and suffix if that team exists | Each string is checked separately, rather than assembling the rendered line. |
| Every team returned by `scoreboard.getPlayerTeams()` | Team display name, player prefix, and player suffix | This scan includes teams not tied to a visible sidebar row, even when an entry had an explicit display component. |

All matches are combined with logical OR. `skyblock` and `rift` are then **replaced** by the scan results, so a Rift match is not permanently latched. The two flags are independent: normalized text containing `SKYBLOCK` sets `skyblock`; a Rift phrase sets `rift`. They need not appear in the same component or row. A Rift phrase alone does not make `isActiveSkyblock()` true, which also requires the Hypixel address gate and a `SKYBLOCK` match.

## Exact Rift phrases

`isRiftLine(String)` uses substring checks for the following literals:

| Literal | Match behavior |
| --- | --- |
| `THE RIFT` | Anywhere in a normalized string |
| `RIFT TIME` | Anywhere in a normalized string |
| `MOTES` | Anywhere in a normalized string |
| `WYLD WOODS` | Anywhere in a normalized string |
| `LAGOON` | Anywhere in a normalized string |
| `DREADFARM` | Anywhere in a normalized string |
| `STILLGORE` | Anywhere in a normalized string |
| `MIRRORVERSE` | Anywhere in a normalized string |
| `LIVING CAVE` | Anywhere in a normalized string |
| `WITHER CAGE` | Anywhere in a normalized string |

These are the code's match literals, not a verified, exhaustive list of current Hypixel Rift areas. No exact equality, word boundary, location-symbol check, or requirement that the match be a location row is imposed.

Component inputs are read using `Component.getString()`; component styling is not inspected. `stripFormatting()` also removes each literal `§` and the immediately following character, without validating that character as a formatting code, and uppercases each remaining character with `Character.toUpperCase()`.

Consequently, literal `§5Rift §dTime: 04:00` becomes `RIFT TIME: 04:00` and matches. The code does strip legacy Minecraft colour codes before matching. It does not trim or collapse whitespace, join team fragments, remove other symbols, or translate text. For example, `Rift  Time` does not match `RIFT TIME`, and a prefix `Rift ` with a suffix `Time` does not match that phrase unless another separately scanned string supplies a match.

## Refresh timing and resets

`PaintBrushClient.onInitializeClient()` calls `ImportedFeatureClient.initialize()`, which registers an unconditional `ClientTickEvents.END_CLIENT_TICK` callback. That callback updates `SkyblockContext` before calling `VisualHealthController.tick()`. Context scanning continues even when Max 10 Hearts is disabled.

| Trigger | Result |
| --- | --- |
| First eligible tick, starting with a zero countdown | Scan immediately, then set the countdown to 20. |
| Later eligible ticks | Scan when `ticksUntilScoreboardScan-- <= 0`. The comparison uses the countdown's value before decrementing it, so scans are **21 callback ticks apart**, approximately 1.05 seconds at 20 client ticks per second. |
| `refreshNow(client)` | Scan immediately and reset the countdown to 20 when the address/world gate passes. |
| A local-player attribute packet that includes `Attributes.MAX_HEALTH`, with an available max-health attribute | `VisualHealthController.onAttributesApplied()` captures the server max health, calls `refreshNow()`, then reconciles health. The mixin calls this after `ClientPacketListener.handleUpdateAttributes()`. Unrelated attribute packets do not trigger this refresh. |
| Non-Hypixel address or null client world, observed by either refresh entry point | Clear `skyblock` and `rift`, and reset the countdown to zero. |
| An eligible scan with no Rift matches | Set `rift` to false. No separate unknown-location state exists. |

`SkyblockContext` has no connection-event reset or world-object identity check of its own. If a world changes while the Hypixel address and non-null-world conditions remain true, the cached flags and countdown can carry over until a scheduled scan or `refreshNow()`. A missing sidebar does not guarantee that Rift becomes false, because the all-teams scan can still match.

## Effect on health and other features

The normalization gate is exactly:

```java
FeatureSettings.isMaxTenHeartsEnabled() && isActiveSkyblock() && !rift
```

`maxTenHearts` defaults to false in `ImportedFeatures.Skyblock`; the existing settings UI describes it as scaling SkyBlock health to ten hearts outside the Rift. `VisualHealthController` additionally requires a remembered server max health above 20 for normalization. Its packet/HUD hooks scale health to the 0–20 range and report a HUD max health of 20 while normalization applies.

When a refresh detects Rift text, the gate becomes false. On reconciliation, previously applied visual max-health changes are undone using the saved attribute snapshot, and remembered server health is restored. The HUD and health-packet helpers also consult the same gate. The detector itself does not send a server command or change server-side health.

The Rift flag does **not** gate No Barrier Effects: that uses its own setting plus `isActiveSkyblock()`. The separate paintbrush `SkyBlockSession` listens to HM API location updates and checks `serverType == SKYBLOCK`; it neither records Rift presence nor feeds `SkyblockContext`. Garden detection separately reads tab-list text. Neither mechanism is a fallback for this Rift check. The Rift path does not inspect chat, tab-list entries directly, item/NBT data, coordinates, or another mod's Rift state.

## Limits established by source inspection

- **Possible false positives:** any scanned string containing a listed substring sets the flag, including unrelated or retained team metadata. Broad matches such as `MOTES` and `LAGOON` are not restricted to location text. A false positive suppresses Max 10 Hearts where it would otherwise apply.
- **Possible false negatives:** if the scanned data contains `SKYBLOCK` but none of the ten Rift phrases, Rift is treated as absent. Changed text, unlisted areas, split text fragments, or changed spacing can therefore permit normalization inside the Rift. If the SkyBlock match is also absent, normalization remains off because the SkyBlock gate fails.
- **Transition delay:** cached results can lag a changed scoreboard until the next scan; the max-health packet path may refresh earlier, but it cannot detect scoreboard text that has not arrived yet. There is no dedicated scoreboard-update hook in this detector.
- **Coverage:** the existing test trees have no dedicated `SkyblockContext` or `VisualHealthController` tests. Existing configuration/migration tests mention `maxTenHearts`, but do not establish Rift detection correctness. The integration notes already identify live health/Rift transitions as requiring verification.

These are consequences of the implementation, not reproduced reports of failures on Hypixel. This discovery run performed source inspection; it did not enter a live Rift session or validate the phrases against current server output.

## Documentation packaging

This file is repository documentation at `docs/rift-detection.md`, outside `src/main/java` and `src/main/resources`. The inspected Gradle build has no rule copying root `docs/` into the mod JAR or the sources JAR; its resource processing only expands the version in `fabric.mod.json`. The optional UI fixture uses separate `src/uiTest` sources/resources. No runtime resource, mod entrypoint, settings option, or localization entry is added for this document, so it is not exposed in the compiled mod or player-facing UI.

## Source references

Links are pinned to the audited commit so these findings remain reviewable if the implementation changes.

| Evidence | Source |
| --- | --- |
| Cached flags, refresh entry points, host gate, scanned inputs, exact phrases and formatting removal | [`SkyblockContext.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/features/skyblock/SkyblockContext.java#L13-L175) |
| Client initialization | [`PaintBrushClient.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/paintbrush/PaintBrushClient.java#L84-L87) |
| Tick registration and context-before-health ordering | [`ImportedFeatureClient.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/features/skyblock/ImportedFeatureClient.java#L10-L25) |
| Refresh on max-health packets, normalization and restoration | [`VisualHealthController.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/features/skyblock/VisualHealthController.java#L21-L127) |
| Health-packet hooks | [`ClientPacketListenerMixin.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/features/skyblock/mixin/ClientPacketListenerMixin.java#L15-L25) |
| HUD hooks | [`GuiMixin.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/features/skyblock/mixin/GuiMixin.java#L18-L47) |
| Settings adapter, default, and existing UI | [`FeatureSettings.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/features/skyblock/FeatureSettings.java#L7-L12), [`ImportedFeatures.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/paintbrush/ImportedFeatures.java#L20-L24), [`ImportedSettingsCategories.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/paintbrush/ImportedSettingsCategories.java#L30-L38) |
| Separate HM API SkyBlock session and Garden detection | [`SkyBlockSession.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/paintbrush/SkyBlockSession.java#L11-L37), [`GardenDetector.java`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/src/main/java/dev/eviemod/features/garden/GardenDetector.java#L14-L24) |
| Existing live-verification limitation | [`kabeewie-integration.md`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/docs/kabeewie-integration.md#L143-L145) |
| Build version, Java/resources configuration, sources JAR and optional fixture | [`build.gradle`](https://github.com/eveternet/eviemod/blob/33b8e41c7f74f8288a56b339eebea0d7cce7a81d/build.gradle#L1-L70) |
