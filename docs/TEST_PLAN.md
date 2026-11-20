# Test plan: Item Beacon

**Inventory search and sort (plugin 0.2.0, client 0.2.0, tests 39-48)**, after slices 1 and 2 (tests 1-38, all checked by the owner for v0.1.0). Nothing has been tried
by a player yet. Checked here: the mod builds against 26.3 / Fabric API 0.164.0 and loads in a real Fabric Loader
(mixin audit), 10 client tests, 20 plugin unit tests, and the self-test on a real Paper 26.3-211 server (24 checks).
Tests 1-22 are without the mod (any client); 23-36 with it.

## Setup (once, about 5 minutes)

1. Close Minecraft (Windows won't replace a mod jar the game has open).
2. Double-click `run\build-and-install.cmd`. It stops the test server (`..\blueprints-mc\run\local-paper`), builds
   and tests both parts, installs `plugins\ItemBeacon.jar` and the mod in `%APPDATA%\.minecraft\mods\`, then offers
   to start the server. For tests 1-22, join **without** the mod (another launcher profile, or move the jar out).
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

### With the mod (client 0.1.0)

Launch **fabric-loader-26.3** with the mod and join. `/itembeaconmod` should say "connected".

| # | Do this | Expect |
|---|---|---|
| 23 | Look at the demo beacon, and hold a beacon item | The owner's chosen look: lodestone sides with a glowing cyan core, compass-rose top, lodestone bottom. Same for the item in hand and in the inventory. Plain lodestones still look like lodestones. |
| 24 | Creative inventory | An "Item Beacon" tab with the beacon; search "beacon" finds it. One taken from the tab stacks with a crafted one. |
| 25 | Right-click the demo beacon | The Item Beacon screen (not the chest menu): item icons with totals, iron shows 126, most first. |
| 26 | Hover an item | Its name, "N in M containers". |
| 27 | Type "iron" in the search box | Only iron things. Clear it: everything back. |
| 28 | Click Blocks, Tools, Food, Redstone, Ingredients, All | The grid narrows to that group (the selected filter's button greys out). |
| 29 | Click "Sort: most first" | "Sort: A-Z", alphabetical; again: back. |
| 30 | Click the iron | Right side: Iron Ingot, 126 in 5 containers; rows nearest first: the chest "(in Red Shulker Box, Bundle)", the barrel, the furnace, the buried chest "below you", the chest minecart; each with xN and distance in m. |
| 31 | "Show waypoints (5)" | "5 waypoints for 10 min." Close the screen: the five glowing outlines (server-drawn until slice 3). |
| 32 | Open one of them | Its outline goes. `/itembeaconmod`: one waypoint fewer. |
| 33 | Back at the beacon: iron, "Nearest only" | Only the nearest is outlined. "Clear waypoints": none. |
| 34 | A container far away (test 18) and one in the Nether (test 19) | Far: a grey row, "last seen N min ago". Nether: "in the Nether", last; neither gets an outline. |
| 35 | Lots of items (a few chests of different things), scroll over the grid and over the list | Both scroll. |
| 36 | Stand near a beacon (with or without the mod) | Enchanting glyphs drift into it. |
| 37 | Client 0.1.1: right-click a beacon holding a block, many times (also with a block in the off hand) | The screen opens; no "Block placed" subtitle, no sound, no flash of the block. Sneak-right-click with a block still places it. |
| 38 | Client 0.1.2: start the game, join, open a beacon **first**, then the creative inventory and search "plan" (or anything) | Search finds items (it was empty). Also after reopening the beacon a few times. |

### Inventory search and sort (plugin 0.2.0, client 0.2.0)

Fill a double chest with a mess: partial stacks of a few blocks, some food, tools, a renamed item, a shulker box with
iron inside, a bundle with an apple.

| # | Do this | Expect |
|---|---|---|
| 39 | Open the double chest | Beside it, on the right (left if the window is narrow): a search bar; Type / A-Z / Count beside the chest's rows; another Type / A-Z / Count beside your inventory rows. Hovering a button explains it. |
| 40 | Type "iron" in the search bar | Everything else darkens; iron stays bright; the shulker box holding iron has a gold frame. Typing "e" doesn't close the screen; 1-9 don't swap slots while typing. Esc closes the screen. |
| 41 | Chest's **Type** | Blocks first in creative-menu order, then tools, food...; partial stacks merged into full ones; the renamed item stays apart; empty slots at the end. |
| 42 | Chest's **A-Z**, then **Count** | Alphabetical; then what you have most of first. Nothing lost or duplicated (count a stack before and after). |
| 43 | Your inventory's **Type** with things in the hotbar and armour on | Only the 27 main slots change; hotbar, armour and off-hand stay exactly as they were. |
| 44 | Do 41-42 in a barrel, a shulker box (placed), your ender chest, a hopper, a dispenser, a chest minecart | Sorted the same way. A furnace or brewing stand has the search bar and your inventory's buttons, but no container buttons. |
| 45 | Your own inventory (E), survival | Search bar and your inventory's buttons beside it; they follow the panel when you open the recipe book. |
| 46 | A second player has the same chest open while you sort | They see it sorted too; nothing lost. |
| 47 | Join a server without the plugin (or a single-player world) | Search bar still there and working; no sort buttons. |
| 48 | Creative inventory | Unchanged (no extra search bar). |

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
   blocks and in loaded chunks; the rest are only in the chat list. **With the mod they're the same outlines for now**:
   slice 3 replaces them with beams, x-ray outlines and on-screen markers.
7. **Not checked by the self-test, only by you:** a player opening, closing, placing and breaking; the menu and the
   screen; the outlines; the compass guard; ender chest contents (they need a player).
8. **The block and item textures are your chosen draft** (`drafts\draft_chosen_*.png`), as `item_beacon_side.png` and
   `item_beacon_top.png` in `fabric-client/src/main/resources/assets/itembeacon/textures/block/`. Replace them with your
   redraw (same names, 16 x 16, opaque; a test checks) and rebuild. The bottom is a plain lodestone top.
9. **Filters come from the creative tabs** (Blocks = building, coloured, natural and functional blocks; Tools = tools
   and combat). An item can be in two (redstone blocks are blocks too). Say if you'd like other groups.
10. **Particles**: two glyphs per beacon every quarter second, only near players; `particles: false` in the config
   turns them off. Not seen in a game yet.
11. **Without the mod, right-clicking a beacon with a block in hand still flashes the block** (and plays its place
   sound) before the server takes it back: an unmodded game sees a lodestone and places the block itself. Nothing
   server-side can stop that. The mod fixes it from client 0.1.1.
12. **Sorting needs the mod and plugin 0.2.0**: players without the mod have no buttons (no command either; say if
   you want `/sort`).
13. **Sort and search sit beside the panel**, not inside it (there's no room inside a chest screen). At very small
   windows with the recipe book open they can overlap it.
14. **Type order** comes from the creative menu (the mod reads it): items it doesn't list (rare) go last, by name.
15. **Nothing on screen is checked here**: the cloud workspace can't run the 26.3 client. Likely suspects if
   something looks wrong: the screen's layout at your GUI scale (send a screenshot), an item that shows as a barrier
   (the mod couldn't read it), Sodium/Iris not calling Fabric's model hook (the beacon stays a lodestone).
