# Item Beacon client protocol, version 1

Plugin messages on channels `itembeacon:<name>`. All encoding is Minecraft-compatible (VarInt, VarLong, big-endian
Int32, UTF = VarInt byte length + UTF-8, Bool = one byte). The code is shared: `beacon-plugin/src/main/java/dev/whizzle/itembeacon/model/`
(`Protocol`, `Wire`, `BlockSet`, `ItemKey`) is compiled into both the plugin and the mod. Change it there, and bump
`Protocol.VERSION` when any message's bytes change.

| Channel | Direction | Body |
|---|---|---|
| `hello` | C->S | VarInt protocol version. Send after joining (and after a reload). |
| `welcome` | S->C | VarInt server protocol, VarInt radius (blocks), VarInt waypoint lifetime (seconds). A mod with another version should stay quiet. |
| `query` | C->S | VarInt n (1-64), n x (Int32 chunk x, Int32 chunk z). Chunks the mod had before the welcome. |
| `chunk` | S->C | Int32 chunk x, Int32 chunk z, VarInt n, n x Int32 packed position `(y << 8) \| (z << 4) \| x` (x, z 0-15). That chunk's Item Beacons (lodestones to draw as beacons). Sent unasked right behind each chunk the server sends a modded player, answered for queries (only chunks the server sent that player, 64 per tick), and pushed whole whenever a beacon in it changes. Forget a chunk when it unloads. |
| `summary` | S->C | Sent when the player right-clicks a beacon; the mod opens the picker. VarInt session, VarInt page (0-based), VarInt pages, UTF beacon dimension (`minecraft:overworld`), Int32 beacon x, y, z, VarInt n, n x item. Item: VarInt id (its number in this session), item bytes (VarInt length + bytes), VarLong total, VarInt containers, Bool only last seen. |
| `pick` | C->S | VarInt session, VarInt item id. Asks for that item's places (waypoints don't change). Ignored unless the player is still within 8 blocks of the session's beacon. |
| `places` | S->C | VarInt session, VarInt item id, VarInt n (at most 4000, nearest first, other dimensions last), n x place. Place: UTF id, UTF kind, UTF dimension, Bool same dimension as the beacon, Int32 x, y, z, VarInt count, Bool live, VarLong last seen (epoch ms), UTF inside. |
| `show` | C->S | VarInt session, VarInt item id, Bool nearest only, Bool outlines. Sets the player's waypoints to that item's places in the beacon's dimension (or only the nearest), replacing any before. With outlines the server also draws its glowing outlines (client 0.1.0 asks for them; slice 3's mod draws its own and sends false). Same 8-block rule. |
| `clear` | C->S | Empty. Clears the player's waypoints. |
| `opened` | S->C | UTF container id. The player opened a container they had a waypoint to: clear that waypoint. |

**Pages.** Minecraft refuses a clientbound custom payload over 1 MiB, and a big storage room's summary can exceed
that, so the summary is split into pages of at most 256 KiB of items. Collect pages `0..pages-1` of one session, then
show the picker; a summary with a newer session replaces an unfinished one. There is always at least one page (an
empty one when the player owns nothing).

**Item bytes** are Paper's `ItemStack#serializeAsBytes` of a single item: `ItemStack.CODEC` encoded with registry NBT
ops, a `DataVersion` added, `NbtIo.writeCompressed` (read from the Paper 26.3 jar: `CraftItemStack.serializeAsBytes` ->
`MCUtil.serializeTagToBytes`). The mod
reads them with `NbtIo.readCompressed`, runs the data fixer (`References.ITEM_STACK`) if the version differs from
the game's, and decodes with `ItemStack.CODEC` (registry ops); an item it can't read shows as a barrier. Two items group together exactly when their bytes are equal.

**Places.** `id` is `b:<world uuid>:<x>:<y>:<z>` for a block, `e:<entity uuid>` for an entity (its position is where
it was last seen), or `ender` for the player's ender chest (the position is then the nearest ender chest in the
beacon's dimension; same dimension is false if there is none). `kind` is the block or entity type key (`chest`,
`chest_minecart`, `ender_chest`). `inside` lists where the items sit, `"; "`-separated: an empty part means loose in
the container (`"; Red Shulker Box"` = some loose, some in a red shulker box). Places in another dimension get no
waypoint; show them in the list with a label.

**Waypoints.** `show` sets the player's waypoints (same dimension only); the server clears one when the player opens
that container (and sends `opened`), all after the lifetime, or all on `clear`. The mod clears its own copy on the same
timer. Without the mod, the chest menu sets them on a click, with outlines.

Rate limit: at most 40 messages per second per player; more are dropped. Malformed messages are ignored.
