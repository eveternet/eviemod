# Live-test follow-up for the combined preview

The user-tested Minecraft 26.2 JAR matched the delivered `c2d601a` artifact
byte for byte (SHA-256 `7805f4e0b5c44eba1b71b33f68a351f3e3144233725be61d81a6dea4eddd65b0`).
Read-only inspection of the instance identified in the supplied log confirmed
Wishing Compass, all three experiments and click protection were enabled.
The installed Skyblocker experiment solvers and SkyHanni addons/Superpairs
overlays were disabled; Skyblocker's compass solver was enabled. User settings
and modpack files were not changed.

The supplied log contains four Skyblocker compass solutions from eight uses;
two successful pairs were used only two seconds apart. Its wait messages belong
to Skyblocker, not Eviemod. All three experiments were completed, but the log
does not record the server's container payloads, round/instruction components,
Eviemod activation gates or extracted overlay count. It contains no Eviemod
exception or required-mixin failure. The active Metal backend is a reproduction
condition, not evidence that Eviemetal caused either failure. Its implementation
remains excluded from this task.

## Verified defects and boundary corrections

Eviemod's five-second minimum between compass uses rejects the observed two-second
workflow. Repeated attempts within the same window also destroyed an in-progress
capture and extended its cooldown. The revised solver keeps the existing capture,
ends a qualified unique trail after a short quiet interval, and uses five seconds
only as a timeout. It accepts a verified repeated pass or a sufficiently sampled
coherent pass, retaining competing-trail, unknown-prerequisite, skew, uncertainty
and coordinate rejection. The previous five-degree intersection guard also
rejected useful geometry permitted by the advertised eight-block movement;
numerical parallel rejection and the computed uncertainty now govern that case.
Early completion cannot establish ownership of an indistinguishable foreign
trail or anticipate a later competing stream; this is documented rather than
claimed as solved attribution.

Compass arming now observes only the vanilla outbound use-packet send boundary
after Fabric's short-circuiting use callbacks. Cancelled/unsent attempts do not
arm it. Happy-villager packet origins are the protocol's server-defined centres;
spawn count/offsets control visual particles and no longer discard otherwise
valid trail evidence. Actual 26.1.2 bytecode and 26.2 source have the same narrow
`ClientCommonPacketListenerImpl.send` → `Connection.send` boundary.

Experiment guidance moves from vanilla `extractSlot` tail to Fabric's screen
after-extraction event, with its own drawing stratum and translated container
coordinates. Installed SkyHanni can cancel slot extraction, so a tail-only hook
cannot reliably own overlays. The location API already used by Quick Navigation
also qualifies the SkyBlock gate, while the actual Hypixel connection remains
mandatory. Manual pick-block compatibility handles the identified SkyHanni input
rewrite, although its helper was disabled in this observed instance. Existing
title/round/instruction prerequisites were compared with maintained references;
they are not relaxed on speculation. These corrections address demonstrated
boundaries; the supplied log does not establish the complete no-overlay cause.

## Navigation and targeted evidence

Fresh/reset navigation has fourteen empty editor positions and zero runtime
buttons. Existing saved buttons remain authoritative. **Use held item** copies
a count-one item through Minecraft's registry-aware codec, preserving SkyBlock
custom data, textured heads, dyes and rendering components. It never changes
the live inventory stack. Reset clears only the selected position.

`/eviemod diagnose` writes bounded solver observations to the local log after an
attempt. Experiment evidence includes accepted packets, overlay extraction and
draw counts, current/last closed menu, parsed round/instruction and activation
gates; compass evidence includes own sent uses, packet shape/count, qualification
and solver status. Diagnostic state is transient and scoped to the connection;
no player inventory/chat/address dump or external upload is performed.

Version **1.4.0.1** identifies this correction build. The unchanged main/addon
separation and both supported main-mod targets remain. Tests and CI fixtures
do not establish live Hypixel or full modpack success. See the updated
[live checklist](live-test-minor-features.md) for the focused next validation.
