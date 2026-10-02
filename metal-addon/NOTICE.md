# Third-party source

The Metal backend, shader translator, and JNI implementation are derived from
[Im-Fran/MetalCraft](https://github.com/Im-Fran/MetalCraft), pinned at commit
`a2cc82780d01a51d00a297f75cf07c221d7c8700` (Francisco Solis and contributors).
This addon is distributed under GPL-3.0-only; see LICENSE. It is a separate
artifact and does not relicense the MIT eviemod artifact.

Changes include Minecraft 26.1.2 backend interfaces, color/depth pipeline state,
opt-in/platform/conflict checks, deferred native attachment, startup fallback,
native cleanup/error handling, separate packaging, and regression checks.
Upstream Sodium integration and developer screenshot helpers are excluded.

The addon bundles LWJGL shaderc and SPIRV-Cross modules and their macOS arm64
natives. Their JARs retain their upstream license notices.
