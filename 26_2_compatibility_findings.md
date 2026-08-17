# Paper 26.2 compatibility findings

- PaperMC confirms support for 26.2 and advises server owners to back up worlds because upgraded worlds cannot be downgraded.
- Paper 26.2 uses a new versioning scheme instead of the old `-R0.1-SNAPSHOT` suffix.
- Paper 26.2 ships Adventure 5, which removes deprecated Adventure APIs. Core does not use BookMeta or deprecated ClickEvent/HoverEvent APIs.
- The relevant Core network modules use Bukkit/Paper scoreboard APIs and ProtocolLib via runtime soft-dependency; they do not use NMS packet classes or 26.2-specific entity classes.
- 26.2 API changes mentioned by Paper affect bed PDCs, cube mobs/Slime hierarchy, Vex owner access, spawn flags, ignite events, dripstone, trim registry and client brand. None are used by Core.

Sources:
- https://papermc.io/news/26-2/
- https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2

PaperMC also confirms that 26.2 introduces API changes around beds, cube mobs, Vex ownership, spawn flags, ignition events, dripstone and Adventure 5. Core does not use any of those APIs. Its relevant code uses Bukkit scoreboard, entities, scheduling and runtime ProtocolLib hooks, so the required build change is to target the released Paper API while retaining ProtocolLib as a guarded soft dependency.
