# Context and parser bounds

- `SkyblockContext.isHypixelServer` reads the client's current multiplayer connection
  and saved connection address. Its shared address parser accepts `hypixel.net` and
  DNS-label subdomains, optionally with a root dot and valid port. HM API location
  data alone cannot enable a release SkyBlock session or schedule a pack reload.
  This is a hostname scope check, not cryptographic server authentication; it does
  not attempt to replace Minecraft's connection handling or resolve DNS itself.
  The existing development-only local fixture allowance remains available.
- `RarityCache` keeps at most 256 recently used stacks on the client thread. It
  compares the references of Minecraft's immutable `CUSTOM_DATA`, `LORE`, and
  `TOOLTIP_STYLE` component values, plus emptiness. Supported component updates
  replace values, including updates to an existing stack. Cache hits do not copy,
  serialize, hash, or deeply compare NBT or pet JSON. Invalid/null parses are cached.
  In-place mutation through unsupported mutable component internals is outside this
  contract. All rendering paths use the cache, independently of rarity-memory;
  remembered rarity is still reconciled separately and never cached as a fresh parse.
  Settings/context clearing also clears parsed entries.
- Pet JSON is limited to 65,536 Java string characters before parsing. Pet metadata
  is normally a small record of type, tier, experience and identifiers; this generous
  allowance leaves room for additional fields. Oversized/malformed metadata remains
  unknown and does not fall back to lore or remembered rarity.
- Explosive Shot parsing accepts up to 128 digits in each numeric field, counting
  integer and fractional digits together, and up to 512 characters for the full
  message before regex matching. This admits numbers vastly beyond billions times
  any plausible mob count. Commas and a decimal point remain supported. This bounds
  text processing; it does not cap damage to a gameplay-specific numeric value.
- Workflow action SHAs were resolved from the existing major-version tags through
  GitHub. Annotated tags were dereferenced to commits. The workflow retains its
  existing permissions, triggers, build, artifacts, and tag-only release behavior.
