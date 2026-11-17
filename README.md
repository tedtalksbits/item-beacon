# Item Beacon

Find your own stuff. Craft an Item Beacon, stand at it, browse everything stored in your containers (with totals),
pick one item, and get waypoints to every container holding it. A Paper plugin (ownership, contents, search; works
for everyone) plus, from slice 2, a Fabric client mod (the picker screen, beams, an x-ray outline through the ground,
on-screen markers). Players without the mod get a chest-style menu and glowing outlines.

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
- `/itembeacon info`: who owns the container you look at. `/itembeacon demo` (creative): a beacon and some filled
  containers around you. `/itembeacon owner <player|clear>` (ops): give the container you look at to someone, or to
  nobody.

## Without the mod

Right-clicking a beacon opens a chest-style menu of your items, most first, totals in the tooltip. Click one: the
menu closes, the containers holding it get a glowing outline only you see (through walls, up to 160 blocks away), and
chat lists them with distance, height and "last seen". The bottom row has previous/next page and Clear waypoints.

## Installing

- Server: `beacon-plugin-<version>.jar` in `plugins/`, restart. Data: `plugins/ItemBeacon/players/` (one small file
  per player) and `enderchests.bin`; beacons are stored in the world's chunks.
- Players: the client mod comes with slice 2.

Development: `AGENTS.md`, `docs/`.
