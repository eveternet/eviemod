# Third-party source

The Metal backend, shader translator, and JNI implementation are derived from
[Im-Fran/MetalCraft](https://github.com/Im-Fran/MetalCraft), pinned at commit
`a2cc82780d01a51d00a297f75cf07c221d7c8700` (Francisco Solis and contributors).
This addon is distributed under GPL-3.0-only; see LICENSE. It is a separate
artifact and does not relicense the MIT eviemod artifact.

Changes include Minecraft 26.2 backend/surface interfaces, color/depth pipeline state,
opt-in/platform/conflict checks, deferred native attachment, startup fallback,
native cleanup/error handling, separate packaging, and regression checks.
Sodium terrain compatibility adapts the loose region/time/id uniforms to the
26.2 Blaze3D frontend in Sodium 0.9.2. It preserves Sodium arena ownership,
recycling and batching with the addon's ordered uploads and synchronization.
The native bridge and shader utilities are shared with the retained 26.1.2 addon.
Development launch/screenshot fixtures are separate and excluded from the distributed JAR.

The addon bundles LWJGL shaderc and SPIRV-Cross modules and their macOS arm64
natives. Their JARs retain their upstream license notices.
