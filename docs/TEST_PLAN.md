# Test plan: Item Beacon

**Slice 1, the server side (plugin 0.1.0, no mod yet).** The self-test passed on a real Paper 26.3-211 server
(22 checks) and 19 unit tests pass; **nothing has been tried by a player yet**. Everything below is what needs you:
opening containers, the menu, the outlines.

## Setup (once, about 5 minutes)

1. Double-click `run\build-and-install.cmd`. It stops the test server (`..\blueprints-mc\run\local-paper`), builds
   and tests the plugin, installs `plugins\ItemBeacon.jar`, then offers to start the server. (No client mod yet: it
   skips that part.)
2. Join with any client (no mod needed for slice 1).
3. In creative, face open ground and run **`/itembeacon demo`**: an Item Beacon 3 blocks ahead, a chest to one side
   (iron loose, in a red shulker box and in a bundle), a barrel to the other, a furnace behind the beacon, a chest
   buried below, and a chest minecart. Iron is in all of them (126 total). You also get a beacon item.

## Tests

| # | Do this | Expect |
|---|---|---|
| 1 | Hover the beacon item | "Item Beacon", a lodestone. |
| 2 | Recipe book, search "item beacon"; crafting table: compass, lodestone, eye of ender in one column (top to bottom), in the left, middle and right column | An Item Beacon each time. A lodestone that is already an Item Beacon doesn't work as the lodestone. |
| 3 | Place the beacon, `/itembeacon info` looking at it | "That is an Item Beacon". |
| 4 | **Survival:** break it with a pickaxe | Drops 1 Item Beacon, not a plain lodestone. |
| 5 | Middle-click a beacon (creative, and survival with one in your inventory) | You get an Item Beacon, never a plain lodestone. |
| 6 | Right-click the demo beacon | A chest-style menu "Item Beacon": your items, most first. Hover iron: "Total 126 in 5 containers". |
| 7 | Click the iron | The menu closes. Chat: iron, 126 in 5 places, then a line each, nearest first, with distance and "below you" for the buried chest; the chest line says "in Red Shulker Box / Bundle". Glowing outlines on the chest, barrel, furnace, buried chest (visible through the ground) and the minecart. |
| 8 | Open the barrel | Its outline goes; the others stay. |
| 9 | Right-click the beacon, click "Clear waypoints" | "Waypoints cleared." All outlines go. |
| 10 | Pick iron again, wait 10 minutes | The outlines go by themselves. |
| 11 | Right-click the beacon holding a compass, sneaking and not | Never "Compass bound"; not sneaking opens the menu. Sneak-right-click with a block places it. |
| 12 | A second player (or a second account) right-clicks your beacon | Their own items only (or "Nothing here yet"). |
| 13 | **Survival:** place a new chest, put something rare in it, open the beacon | The item is listed; picking it outlines that chest. |
| 14 | Find a chest nobody owns (an old one, or `/itembeacon owner clear` on one as op), open it, then check `/itembeacon info` | "Owned by <you>". A second player opening it doesn't take it over (`info` still says you). |
| 15 | Two chests side by side, one yours, one placed by another player | Both see both halves' items. |
| 16 | Break one of your chests (or blow it up), open the beacon | Its items are gone from the list. |
| 17 | Put something in your ender chest; open the beacon, pick it | "in your Ender Chest (nearest ender chest x y z)"; the nearest ender chest is outlined. |
| 18 | Fill a chest far away (more than 64 blocks, ideally beyond view distance), come back, change nothing, open the beacon | Its items are listed; pick one: the line says "(last seen N min ago)", grey. |
| 19 | A chest of yours in the Nether, open a beacon in the Overworld | Listed: "... at x y z, in the Nether", no outline. |
| 20 | Put an item in an item frame, in a chest minecart, in a chest boat; open the beacon | Each is listed; outlines on minecarts and boats are a small glass cube. |
| 21 | Restart the server, open the beacon | Everything is still there (`plugins/ItemBeacon/players/`). |
| 22 | `/itembeacon owner <name>` and `/itembeacon owner clear` on a chest (op) | The chest moves to that player's list, or to nobody's. |

## ⚠️ Known limits and decisions for you

1. **The default radius** (64, config `radius`): try it and tell me if it should change. The radius only decides
   what's reread live; everything you own is listed anyway.
2. **Lodestones can't be moved by pistons** (Paper 26.3 says so; the self-test checks it), so neither can a beacon.
   The piston code is there in case that ever changes.
3. **"First to open it owns it" includes structure loot chests and shared old chests.** `/itembeacon owner` (ops)
   fixes a wrong owner; it works on blocks only, not minecarts or item frames.
4. **Llamas, donkeys and mules with chests aren't counted** (they walk around; tell me if you want them).
5. **Hoppers moving items** aren't followed as they happen: a container's list updates when it's opened and closed,
   when its chunk loads or unloads, and when you open a beacon within the radius.
6. **Glowing outlines** use the server's glowing effect (one colour, aqua); only you see them. Up to 64, within 160
   blocks and in loaded chunks; the rest are only in the chat list. With the mod (slice 2) they become beams, x-ray
   outlines and markers.
7. **Not checked by the self-test, only by you:** a player opening, closing, placing and breaking; the menu; the
   outlines; the compass guard; ender chest contents (they need a player).
