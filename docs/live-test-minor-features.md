# Combined feature live-test checklist

Build: Eviemod **1.4.0.0**, combining Quick Navigation (#18), Wishing Compass
(#19) and Experimentation Table helpers (#20). Run the matching **26.1.2** or
**26.2** main-mod JAR with Java 25 and Fabric API. Use one Eviemod JAR per profile.
The Eviemetal 26.2 PR (#17) and performance-roadmap PR (#12) are excluded.
Local tests and CI fixtures do not establish live Hypixel compatibility.

## Setup and shared settings

- [ ] Back up the profile's `config/eviemod` folder; use a disposable test profile
  for resets or malformed-file checks. Record Minecraft/Fabric/modpack versions.
- [ ] On fresh settings and an existing configuration missing the new fields,
  all three features and incorrect-click protection start off. Existing choices
  such as rarity backgrounds and Party Finder settings survive the upgrade.
- [ ] Enable each feature in `/eviemod`, save, reopen settings and restart.
  Choices persist. Edit navigation tabs after changing Mining/Experiments
  settings, then return and save: neither those choices nor tab edits are lost.
- [ ] Disable each feature independently; the other enabled features still work.
  Reset controls leave new features off. In the disposable profile, malformed
  settings report an error and preserve the original file; repair it and use
  `/eviemod reload` to recover.

## Quick Navigation

- [ ] Enable **Quick Navigation**. Check the fourteen default tabs in player,
  short-chest and six-row SkyBlock menus. Check menu highlights and tooltips,
  recipe-book shifts, GUI scales, window resize and resource packs. Outer tabs
  can clip at very large GUI scales; inventory slots should remain uncovered.
- [ ] Test one ordinary shortcut and a double-click warp. The warp needs a second
  click within one second; an expired confirmation sends nothing. Fast clicks
  across menus respect the shared one-second cooldown. Server rejection of a
  command should leave normal inventory controls usable.
- [ ] Carry an item and try left/right/middle clicks and releases on tabs: no
  command, item drop or unintended inventory action. Ordinary slot clicks work.
- [ ] In **Edit tabs**, change icon, command, title pattern, tooltip, hide and
  activation; switch tabs and resize. **Done** keeps edits; **Cancel** discards
  edits; **Reset tab** restores only that tab. Invalid icon/pattern blocks saving
  until corrected. Editing while disabled leaves navigation off.
- [ ] Disconnect, switch server, leave SkyBlock and enter creative mode on a test
  world: navigation stays inactive outside its supported context.

## Wishing Compass

- [ ] Enable **Mining → Wishing Compass Solver** and Hypixel's **Crystals** tab
  widget. Outside the nucleus, manually use a Wishing Compass, wait five seconds,
  move at least eight blocks sideways in the same region and use it again.
  Wait another five seconds: a usable pair shows an estimated marker, distance,
  coordinates and uncertainty ring. Navigate across a region boundary; the
  solved marker persists. Check F1, camera angles and marker readability.
- [ ] Test near-parallel uses, too little separation, incomplete trails, crowded
  simultaneous compass uses and nucleus starts. Unsupported/ambiguous data
  should withhold the marker while normal compass use continues.
- [ ] Change a crystal's status, Jungle Key possession, or King's Scent presence;
  old observations invalidate. Scent countdown ticks alone should preserve them.
  Missing/blank/loading effect disclosure in Goblin contexts should withhold
  solving rather than assume no scent.
- [ ] **Clear compass observations**, disable the solver, change lobby, leave the
  Hollows, disconnect/reconnect, and wait past five-minute expiry: no stale marker
  or pending observation returns. Two observations never cause automatic use.

## Experimentation Table

- [ ] Enable **Experiments → Chronomatron**; watch a complete round, including a
  repeated note, then replay manually. The green outline advances one note at a
  time. Skipped/incomplete observations safely suppress guidance.
- [ ] Enable **Ultrasequencer**; watch the numbered reveal and replay manually.
  Remembered numbers and the next green outline agree with the server sequence.
  A new round clears the previous sequence.
- [ ] Enable **Superpairs**; reveal cards manually. Hidden known cards show copied
  items, counts and hover tooltips; yellow marks known pairs and green the known
  match for the revealed first card. Distinct enchantments/counts stay distinct.
- [ ] With **Protect incorrect clicks** off, mistakes remain possible and invalidate
  sequence guidance. With it on, only a fully known wrong replay choice or known
  nonmatching second Superpairs reward is blocked; blocked clicks do not advance
  guidance. Unknown cards and powerups remain clickable. No automatic clicks.
- [ ] Close/reopen mid-round, switch experiment/tier, change lobby/world, toggle
  a helper off/on and test delayed server updates. Old memory never returns;
  stakes/reward/unsupported screens and unusual input modes keep normal behavior.

## All features together and modpack coexistence

- [ ] Enable all features and switch between a compass solve, inventories and
  experiments. Navigation clicks should not consume experiment choices; remembered
  tooltips/slot outlines should not cover navigation controls or move live items.
- [ ] Repeat with the intended Skyblocker/SkyOcean, inventory search, resource packs
  and rendering mods. Disable overlapping navigation/experiment solvers first;
  then check each overlap deliberately for duplicate tabs, overlays and click
  interception. Report the exact conflicting toggle/mod/version if found.
- [ ] Repeat on each Minecraft version you intend to use. The JARs contain identical
  feature logic with version-specific adapters; never install both variants.
  Eviemetal compatibility is a separate test if you already use that addon.

Known limits: current server labels, acknowledgment timing and particle behavior
remain live-test assumptions. Compass particles have no originating-player ID;
indistinguishable foreign-only trails cannot be perfectly attributed. Estimates
do not prove a shared target or identify a destination/entrance by name.

For a failure, save the feature/toggles, reproduction steps, expected/actual
behavior, Minecraft/modpack versions and relevant `latest.log` excerpt.
