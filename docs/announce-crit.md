# Dungeons and Announce Crit

Dungeons contains Announce Crit and the existing Noamm Score Sync control (previously under Chat Commands). Score Sync retains its `features.skyblock.noammScoreSync` persistence key, availability condition, and all runtime behavior; no migration is needed. Announce Crit is independent of Noamm and stores its options under `features.dungeons`. Its enable and party-chat settings default to false, including missing keys and resets.

Announce Crit observes Fabric's non-cancelling `ClientReceiveMessageEvents.GAME` event, the existing system-chat integration used elsewhere in Eviemod. Overlay messages are ignored. `Component.getString()` supplies plain text; the exact anchored Explosive Shot summary accepts singular/plural enemies. No dungeon, boss, or server-location gate is added. The behavior is based on the reference supplied in the task, not a new audit of Noamm source.

Parsing and rendering are independent of Minecraft. Positive integer enemy counts and valid nonnegative decimal damage (optionally comma-grouped) are divided with BigDecimal and rounded to the nearest whole number, ties up. US grouping makes 1200000 / 3 display as 400,000 regardless of machine locale. Malformed comma grouping is rejected rather than guessed. There was no existing configurable string-template helper; literal String.replace substitutes every `{damage}`, leaving other text unchanged.

Local output adds a literal chat component. Party mode instead uses the existing PartyCommandController.sendCommand helper with `pc ` followed by the rendered template (the Minecraft command API omits the leading slash). The original Hypixel message is untouched. Normal Minecraft/Hypixel command length and chat restrictions still apply.

Regression fixtures cover parsing, rounding, templates, output selection, disabled/overlay behavior, configuration persistence and invalid-file preservation. These are local tests, not live Hypixel or interactive UI validation. Version 6.0.0 follows the repository's major-version rule for a new settings section.
