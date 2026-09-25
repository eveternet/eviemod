# Party Finder full alert

Enable **Dungeons > Party Finder > Party Full Alert** to show a vanilla Minecraft subtitle when Hypixel sends:

```text
Party Finder > Your dungeon group is full! Click here to warp to the dungeon!
```

The subtitle defaults to `Party is full!` and can be edited in the same group. The feature defaults to off for fresh installs, missing settings, and resets. An empty or whitespace-only subtitle produces no notification.

The client observes the existing Fabric `ClientReceiveMessageEvents.GAME` event, ignores action-bar overlays, and uses the existing Hypixel server context. `Component.getString()` flattens styled siblings and ignores click/hover metadata. Matching strips embedded legacy formatting and outer whitespace, then compares the full supplied line. Player chat quoting that line does not match. The wording is based on the task's reference message; changed server wording is ignored.

Vanilla title/subtitle APIs display the literal configured text for 60 ticks with a 10-tick fade-out. An empty title starts the vanilla timer without adding a large title. This uses the shared vanilla title slot and can replace an active title/subtitle. The original chat component and its warp action are untouched; no command is sent.

Regression tests cover matching, formatting, component flattening, disabled/overlay behavior, custom and blank subtitles, persistence, defaults, and invalid configuration preservation. Fixtures and CI do not constitute live Hypixel or visual gameplay testing.
