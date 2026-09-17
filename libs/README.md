# Pinned Dandelion backport

`dandelion-1.0.0-alpha.21+26.1.jar` is the unmodified nested Dandelion library extracted from the user-supplied `skyblocker-6.10.2+26.1.2.jar`. It is vendored because this exact 26.1 backport was not available from the upstream Maven coordinate (the published alpha.21 targets 26.2). Gradle's local module declaration allows Loom to nest it in the release.

SHA-256: `7a212723222d5410adfb0b241fc46c5a9d84fbf1b7034cfdffad16d15a8edacc`

Source: https://github.com/AzureAaron/Dandelion

Dandelion and its included MoulConfig retain their license files inside the JAR. See `src/main/resources/THIRD-PARTY.txt`. Do not silently replace this backport with a different Minecraft target. Recheck custom option input/rendering and configuration persistence on upgrades.
