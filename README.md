# Item Beacon

Find your own stuff. Craft an Item Beacon, stand at it, browse everything stored in your containers (with totals),
pick one item, and get waypoints to every container holding it. A Paper plugin (ownership, contents, search; works
for everyone) plus a Fabric client mod (the beacon's own look and the picker screen; beams, an x-ray outline and
on-screen markers come in slice 3). Players without the mod get a chest-style menu and glowing outlines.

By **tedtalksbits**. Minecraft / Paper 26.3.

## Using it

- **Recipe:** a column of compass (top), lodestone (middle), eye of ender (bottom), in any column of a crafting table
  or a crafter. Players without the mod see a lodestone named Item Beacon.
- **Right-click the beacon** to open it (sneak-right-click with a block in hand places the block). A compass never
  binds to it. Anyone can use any beacon and only ever sees their own items; place as many as you like.
- **Your containers:** the ones you place, and any nobody owns once you open it for the first time (so storage built
  before the plugin counts). Nothing else changes an owner. A double chest belongs to either half's owner.
- **What counts:** chests (trapped, copper), barrels, shulker boxes, hoppers, droppers, dispensers, furnaces, smokers,
  blast furnaces, brewing stands, crafters, shelves, chiseled bookshelves, decorated pots, lecterns, jukeboxes,
  chest and hopper minecarts, chest boats and rafts, item frames. Items inside shulker boxes and bundles count too
  ("in Red Shulker Box"). Your **ender chest** is listed as "Ender Chest", with a waypoint to the nearest ender chest.
- **Live or last seen:** opening a beacon rereads your containers within the radius (config `radius`, default 64
  blocks around it, all heights). All your other containers are still listed, with what was in them when last seen.
  Containers in another dimension are listed with a label ("in the Nether") and get no waypoint.
- **Waypoints** clear when you open that container, after 10 minutes (config `waypoint-minutes`), or by hand.
- **Breaking** a container (anyone, explosions, fire) removes it from its owner's list.
- **Particles:** enchanting-table glyphs drift into a beacon when a player is near (everyone sees them; config
  `particles: false` turns them off).
- `/itembeacon info`: who owns the container you look at. `/itembeacon demo` (creative): a beacon and some filled
  containers around you. `/itembeacon owner <player|clear>` (ops): give the container you look at to someone, or to
  nobody.

## With the mod

Right-clicking a beacon opens the Item Beacon screen. Left: everything you own as item icons with totals (1.5k),
most first or A-Z, a search box and filters (Blocks, Tools, Food, Redstone, Ingredients, from the creative tabs);
items only known from "last seen" containers have a darker slot. Click one: the right side lists the containers
holding it, nearest first, with the count, distance, "below you", what it's inside, and "last seen" for ones not
read just now; other dimensions last. "Show waypoints" or "Nearest only" sets the waypoints (outlined by the server
until slice 3), "Clear waypoints" clears them. The block and item look like the owner's chosen draft; a creative tab
holds the beacon. `/itembeaconmod` says what the mod knows.

## Inventory search and sort (mod)

Every container screen (chests, barrels, shulker boxes, ender chests, hoppers, furnaces, your own inventory...; not
the creative inventory, which has its own search) gets a search bar beside it. Typing dims every slot that doesn't
match (by name); a shulker box or bundle holding a match gets a gold frame. Search works on any server.

On a server with the plugin (0.2.0 or newer), three sort buttons sit beside the container's slots and three beside
your inventory: **Type** (like the creative menu), **A-Z**, **Count** (what you have most of first). Partial stacks
are merged.

Where the tools sit is up to you: **`/itembeaconmod settings`**. Search bar and container buttons: Right, Left, Top
(a row above the panel) or In the title row (the buttons inside the panel next to the title, the search bar above).
Your inventory buttons: Right, Left, Bottom (below the panel) or In the Inventory row. A spot that doesn't fit (a long
container name, no room above or below) falls back to another. Saved in `config/itembeacon.properties`. Your inventory sort never touches the hotbar, armour or off-hand. Anyone who can open a container can
sort it. Storage only: chests of every kind (minecarts and boats too), barrels, shulker boxes, ender chests, hoppers,
dispensers, droppers.

## Without the mod

Right-clicking a beacon opens a chest-style menu of your items, most first, totals in the tooltip. Click one: the
menu closes, the containers holding it get a glowing outline only you see (through walls, up to 160 blocks away), and
chat lists them with distance, height and "last seen". The bottom row has previous/next page and Clear waypoints.

## Installing

- Server: `beacon-plugin-<version>.jar` in `plugins/`, restart. Data: `plugins/ItemBeacon/players/` (one small file
  per player) and `enderchests.bin`; beacons are stored in the world's chunks.
- Players (optional): `item-beacon-client-<version>.jar` plus Fabric API in `.minecraft/mods` (Fabric Loader 0.19.5,
  Minecraft 26.3).

Development: `AGENTS.md`, `docs/`.
