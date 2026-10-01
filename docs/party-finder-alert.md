# Party Finder full alert

Enable **Dungeons > Party Finder > Party Full Alert** to show a vanilla Minecraft subtitle when Hypixel sends:

```text
Party Finder > Your dungeon group is full! Click here to warp to the dungeon!
```

The subtitle defaults to `Party is full!` and can be edited in the same group. **Party Full Sound** independently enables a local sound for the same message, with an editable sound ID, pitch (0.5–2.0), and volume (0–1). The sound defaults to `minecraft:block.note_block.pling`, pitch 1, and volume 1. Both toggles default to off for fresh installs, missing settings, and resets; adding sound settings never enables sound for an existing subtitle user.

Sound IDs use Minecraft's registry names, such as `minecraft:entity.experience_orb.pickup`; `minecraft:noteblock` is not a registered sound ID. Blank, malformed, and unregistered IDs are silent. ID whitespace is trimmed for playback without changing the saved setting. Blank subtitles suppress only the subtitle; either alert can run independently.

The client observes the existing Fabric `ClientReceiveMessageEvents.GAME` event, ignores action-bar overlays, and uses the existing Hypixel server context. `Component.getString()` flattens styled siblings and ignores click/hover metadata. Matching strips embedded legacy formatting and outer whitespace, then compares the full supplied line. Player chat quoting that line does not match. The wording is based on the task's reference message; changed server wording is ignored.

Vanilla title/subtitle APIs display the literal configured text for 60 ticks with a 10-tick fade-out. An empty title starts the vanilla timer without adding a large title. This uses the shared vanilla title slot and can replace an active title/subtitle. The original chat component and its warp action are untouched; no command is sent.

Sound playback resolves the ID through `BuiltInRegistries.SOUND_EVENT` and uses `SimpleSoundInstance.forUI(event, pitch, volume)`. It plays once per matching system message locally, without positional attenuation, and respects Minecraft's master volume. Non-numeric, non-finite, or out-of-range persisted pitch/volume values reject loading and preserve the original file, following the existing settings policy. Invalid sound names do not prevent loading or subtitle display.

Regression tests cover matching, formatting, component flattening, disabled/overlay behavior, independent sound/subtitle delivery, sound parameters, custom and blank subtitles, persistence, defaults, and invalid configuration preservation. Fixtures and CI do not constitute live Hypixel or visual/audio gameplay testing.
