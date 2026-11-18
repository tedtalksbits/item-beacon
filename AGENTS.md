# Item Beacon: notes for agents

Start here, then `README.md` (what it does) and `docs/PROTOCOL.md`. `docs/TEST_PLAN.md` and `docs/CI.md` cover the
rest. Kickoff brief, mockups and draft textures: `C:\Users\tedan\Documents\software\Claude outputs\item-beacon\` (the
owner's central folder; never put such files inside a project repo, copy in what the project needs).

## What this is

Find your own stuff, on a small Paper server for friends and family (owner: tedtalksbits, MC name TedTalks2). A
craftable station block, the Item Beacon: stand at it, browse everything in your containers with totals, pick one
item, get waypoints to every container holding it. Standalone: its own repo and versions, unrelated to Glowing Glass,
Shulker Styles, Logic Blocks or Blueprints (their patterns were copied, their files never edited).

- `beacon-plugin/`: Paper plugin (Java 25), package `dev.whizzle.itembeacon`.
  - `BeaconItem`: the item (lodestone + PDC `itembeacon:beacon` + item name + custom model data string
    `itembeacon:item_beacon`).
  - `Recipes`: the column recipe (compass / plain lodestone / eye of ender); guards: no other crafting recipe takes a
    beacon (grids, crafters); sulfur cubes don't keep it.
  - `Beacons`: which lodestones are beacons, chunk persistent data `itembeacon:beacons` (Glowing Glass's `Records`).
  - `BeaconKeeper`: right-click opens (compass never binds), placing, breaking, explosions, pistons, drop fallback,
    middle-click (Glowing Glass's `Keeper`).
  - `Containers`: what's a container (block: `TileStateInventoryHolder`; entity: non-living `InventoryHolder`, item
    frames), the owner (`itembeacon:owner` UUID in the block entity's or entity's PDC), double chest halves, contents
    flattened into `Stack`s (shulker boxes and bundles opened, up to 4 deep, "inside" labels).
  - `Index`: every player's `PlayerIndex` (one gzipped file each in `plugins/ItemBeacon/players/`), who holds which
    container, block containers by chunk, ender chest positions (`enderchests.bin`). `refresh(block|entity)` rereads
    one and fixes every owner's entry.
  - `Tracker`: the events that keep the index right (place, open/claim, close, item frames, everything that destroys
    a block or entity, chunk and entity load/unload).
  - `Search`: a beacon session: rereads the player's containers within the radius live, lists everything they own,
    gives the places for a picked item (nearest first, other dimensions last).
  - `Waypoints`: the player's current waypoints (last pick), cleared on open / lifetime / by hand; the no-mod
    glowing outlines (per-player block displays).
  - `ClientBridge`: the protocol (beacon positions per chunk like Glowing Glass; summary pages, pick, places,
    opened).
  - `FallbackMenu`: the chest-style menu and chat list without the mod.
  - `Demo`: `/itembeacon demo`.
- `beacon-plugin/src/main/java/dev/whizzle/itembeacon/model/`: pure Java (`Wire`, `Protocol`, `BlockSet`, `ItemKey`,
  `Stack`, `Entry`, `PlayerIndex`). The mod (slice 2) compiles it in too, as Glowing Glass does. Change it once.
  - `Particles`: enchanting glyphs drifting into beacons near players (config `particles`).
- `fabric-client/`: Fabric client mod (separate Gradle build, Loom), package `dev.whizzle.itembeacon.client`.
  - `ItemBeaconClient`: handshake, beacon chunks (query for chunks loaded before the welcome, 64 per tick), summary
    pages -> `PickerScreen`, places, opened; `/itembeaconmod`.
  - `BeaconCache` (pure): which lodestones are beacons, by chunk; read from chunk-meshing threads.
  - `render/BeaconModels` + `BeaconBlockModel`: the model swap (Glowing Glass's `GlassModels`/`GlassBlockModel`):
    the lodestone's block state model wrapped after baking; the beacon cube emitted at marked positions.
  - Item: `assets/minecraft/items/lodestone.json` selects on custom model data string `itembeacon:item_beacon`,
    vanilla fallback. Textures `item_beacon_side.png` / `item_beacon_top.png` (the owner's chosen draft).
  - `CreativeTab`: an "Item Beacon" tab, the stack built with exactly `BeaconItem.create`'s components.
  - `ui/PickerScreen`: the screen (grid with totals, search, filters, sort, places list, Show waypoints / Nearest
    only / Clear). `ui/Items`: decoding the server's item bytes, filters from the creative tabs.
  - `SummaryAssembler`, `Format`, `Waypoints` (pure): pages, text, the client's copy of the waypoints (slice 3 draws
    them).
- `paper-selftest/`: development-only plugin that checks the plugin on a real Paper server.
- `ci/mixin-audit/`: loads the mod in a real Fabric Loader (no mixins of our own yet; checks the model wrapper and
  that the screen's overrides still override). `ci/client-pure-tests/`: the mod's pure-Java tests without Loom.

## Status (2026-11-17)

- **Client 0.1.1: right-clicking a beacon with a block no longer places it client-side** (owner: block placed
  subtitle, sound and a brief flash, often). The game saw a plain lodestone and predicted the placement; the server
  then reverted it. `UseBlockCallback` returns SUCCESS for a beacon (by `BeaconCache`, `UseRule` mirrors the plugin's
  sneak rule), which sends the click to the server without placing (Fabric's `MultiPlayerGameModeMixin`, read from
  the jar). Unmodded clients still flash (TEST_PLAN limit 11). Not yet seen in game (test 37).

- **Slice 2 (the mod) done, not yet in a real game.** Client 0.1.0, plugin still 0.1.0 (unreleased), protocol 1
  (unreleased, so changed in place: `pick` no longer sets waypoints; new `show` and `clear`).
  - Checked here: the mod compiles against 26.3 / Fabric API 0.164.0; mixin audit OK (all mod classes load in a real
    Fabric Loader, BeaconBlockModel overrides the five position-aware methods, every PickerScreen override still
    overrides); 10 client tests (cache, summary pages in any order, counts, text, waypoints, textures 16x16 opaque,
    model strings match the plugin and the creative tab, no literal mouse buttons, no glyphs the font lacks);
    20 plugin unit tests; self-test 24 checks (new: the creative tab's item is a real beacon and stacks with a
    crafted one; particles tick).
  - **Not checked: anything on screen** (no 26.3 client here): the screen's layout, item decoding on a real client,
    the model swap under Sodium/Iris. `docs/TEST_PLAN.md` tests 23-36.
  - Until slice 3, "Show waypoints" asks the server for its glowing outlines (`show` with outlines = true).

- **Slice 1 (server side) done, not yet in a real game.** Plugin 0.1.0, protocol 1, no mod yet.
  - Checked here: 19 unit tests (model, protocol paging, item, recipe, bridge with MockBukkit); self-test on Paper
    26.3-211, 22 checks: recipe and a crafter crafting a beacon; beacon breakNaturally, TNT, piston (can't move a
    lodestone), chunk unload/load; contents with a shulker box, a bundle, and a bundle in a shulker box; claiming;
    double chest halves; all 18 block container kinds; chest and hopper minecarts, chest boat, item frame; removed
    entities, TNT and an eventless replace are forgotten; radius (live vs last seen); places order, other dimension;
    ender chests found on chunk load, nearest chosen; chunk unload refreshes last seen; 3240 distinct items split into
    summary pages under 1 MiB; saving and loading; the demo.
  - **Not seen in a real game:** a player opening/closing/placing/breaking, the menu, outlines, the compass guard,
    ender chest contents. `docs/TEST_PLAN.md` tests 1-22.
  - CI: workflows written (plugin + self-test; release publishes the plugin jar), **not yet run** (the owner pushes).
- Next: the owner's in-game check of slices 1 and 2 (screenshots of the screen), his redrawn textures; then slice 3
  (mod waypoints: beam, x-ray outline, on-screen marker; `show` with outlines = false).

## Open items

1. The default radius once the owner has tried it (64; config `radius`).

## Decisions already made (don't reopen without asking)

| Decision | Why |
|---|---|
| Name "Item Beacon", folder `software\item-beacon`, namespace `itembeacon`, plugin `ItemBeacon` | Owner |
| Only your own stuff: containers you placed; an unowned container becomes yours the first time you open it (item frames: put in, turn or take out); nothing else changes an owner (except `/itembeacon owner`, ops) | Owner, 2026-11-16 |
| A double chest belongs to either half's owner (both see both halves) | Kickoff |
| Search only at a beacon (pick valid within 8 blocks of it); anyone can use any beacon, several per player | Owner |
| All your containers are always listed; the radius (config, default 64, all heights, horizontal circle) only decides which are reread live; the rest show last-known contents ("last seen") | Owner, 2026-11-16 |
| Other dimensions: listed with a label ("in the Nether"), no waypoint | Owner, 2026-11-16 |
| Broken containers are removed from the list (no "last seen" kept) | Owner, 2026-11-16 |
| Ender chest contents listed as "Ender Chest", waypoint to the nearest ender chest placed by anyone (any ender chest opens your own) | Owner, 2026-11-16 |
| Shulker boxes and bundles inside containers are counted, with "in Red Shulker Box" / "Bundle in ..." | Owner, 2026-11-16 |
| Containers moved by pistons keep their owner; dispensed ones are claimed on first open | Owner, 2026-11-16 (block entities don't move in vanilla anyway) |
| Waypoints: beam + on-screen marker + x-ray outline (mod); clear on open, after 10 minutes (config), or by hand; a new pick replaces them | Owner |
| Without the mod: chest-style menu, glowing block-display outlines only that player sees, chat list | Kickoff ("whatever is trivial") |
| Recipe: shaped column compass / lodestone / eye of ender; the lodestone must be a plain one | Owner |
| Carrier: lodestone; a compass never binds to a beacon | Owner |
| The mod claims right-clicks on beacons (`UseBlockCallback`), so the game never places a held block on one unless sneaking | Owner's bug report, client 0.1.1 |
| Look: C's sides (lodestone with a glowing core) with A's compass-rose top; the owner redraws (drafts in `Claude outputs\item-beacon\drafts\`); bottom: plain lodestone top | Owner; in the mod since client 0.1.0 |
| Enchanting-table glyphs drifting into beacons near players, server-side, config `particles` | Owner (low priority; cheap, so done) |
| The screen follows the mockup (`item-beacon-picker.png`): grid left, places right; filters from creative tab groups (All, Blocks, Tools, Food, Redstone, Ingredients); sort most first / A-Z; items only in last-seen containers get a darker slot | Kickoff |
| Waypoints: picking an item only lists places; "Show waypoints" / "Nearest only" set them (`show`), "Clear waypoints" clears (`clear`) | Mockup's two buttons |
| Index: one file per player, item bytes once per file; items grouped by exact bytes of a count-1 copy (`serializeAsBytes`) | Small, and "same item and components" exactly |
| Summary split into pages of at most 256 KiB (custom payloads over 1 MiB are refused) | Owner, 2026-11-16 |
| Demos only in creative | Same rule as the other projects |
| Release tag = plugin version; the client will keep its own version | Same as the other projects |
| After this project: re-dye for Glowing Glass (8 glowing glass of any colour around 1 dye) | Owner |

## Working rules

- Commit locally; **the owner pushes** to GitHub. Agents commit as `Claude <noreply@anthropic.com>`, per commit:
  `git -c user.name=Claude -c user.email=noreply@anthropic.com commit ...`. Never change the repo's git config or
  rewrite the owner's commits.
- Reports: short, structured, anything needing the owner's decision at the **top, marked ⚠️**. No "•" separators.
- Verify on a real Paper server before claiming something works; MockBukkit is for unit tests only. Say plainly what
  was and wasn't seen in a real game.
- End each slice with `docs/TEST_PLAN.md` (Do this / Expect table, then "⚠️ Known limits and decisions for you") and
  update this file.

## Building and testing

- Plugin: `gradlew build` at the root (Java 25, Gradle 9.8.0). Jar: `beacon-plugin/build/libs/beacon-plugin-<v>.jar`.
- Client: `cd fabric-client && gradlew build`. Jar: `fabric-client/build/libs/item-beacon-client-<v>.jar`.
- Mixin audit: `ci/mixin-audit/run.sh` (Linux, CI; offline `GRADLE="<gradle> --offline"`, JDK 25 first on `PATH`).
  Prints `AUDIT RESULT OK` or fails. Add new mod classes to its target list.
- Self-test: `ci/run-selftest.sh <paper.jar> <new-folder>` (Linux, Git Bash, CI): a throwaway flat-world server
  with `-Ditembeacon.selftest.exit=true`; fails unless `plugins/ItemBeaconSelfTest/report.txt` ends
  `SELFTEST PASSED`. Offline: `PAPER_SEED_DIR=<existing server folder>`. `SELFTEST_ONLY=beacon,gone` runs only steps
  whose names start that way. About 30 seconds.
- Dev loop: `run\build-and-install.cmd` (Windows) or `run/build-and-install.sh` runs `run/DevInstall.java`
  (Glowing Glass's, renamed): stops the local test server, builds and tests, installs the plugin as
  `plugins/ItemBeacon.jar` (and the client mod once `fabric-client/` exists), offers to start the server. Overrides:
  `ITEMBEACON_SERVER_DIR`, `ITEMBEACON_MODS_DIR`, `ITEMBEACON_GRADLE_ARGS`, `JAVA_HOME`, `GRADLE_USER_HOME`.
- CI on every push to `main`: plugin + self-test (`docs/CI.md`). Releases: tag `v<plugin version>`.

## Gotchas

- **Lodestones don't move with pistons** (`PistonMoveReaction.BLOCK` on 26.3); the keeper's piston code is for a
  future version. Block entities don't move either, so containers never move with pistons.
- `World#getBlockAt` doesn't load a chunk, and `Beacons.is` returns false for an unloaded chunk: load it first
  (self-test).
- Server-side copies of a shaped recipe (`Bukkit.getRecipe`) rename the shape's letters: test by shape position.
- A chunk with no player near may be unloaded by the server within seconds (self-test: the unload handler already
  ran); a freshly loaded one may refuse `unloadChunk` for a while.
- Plugin-made changes (`breakNaturally`, `setType`, inventory edits) fire no events: the index notices on the next
  read (beacon within radius, chunk load/unload, open/close). Real players' actions do fire them.
- `Chest#getBlockInventory` is one half's own inventory; `getInventory` is the whole double chest.
- MockBukkit: no real chunk persistence or block entities; test containers in the self-test.
- The flat self-test world is only 3 blocks deep above bedrock: the demo buries its chest as deep as it can (2-4).
- Mod UI (from Shulker Styles, tested in `ResourcesTest`): mouse buttons are SDL-numbered
  (`InputConstants.MOUSE_BUTTON_*`); the GUI font has no ✓ ✕ ≠ ≤ ≥ →. `Screen.extractRenderStateWithTooltipAndSubtitles`
  calls `extractBackground`, then `extractRenderState` (widgets): draw the panel by overriding `extractBackground`
  (super first), never call it yourself. Open screens with `minecraft.gui.setScreen`.
- The server's item bytes are `ItemStack.CODEC` + `DataVersion` + compressed NBT (read from the Paper jar); 26.3 has
  no `DataFixTypes.ITEM_STACK`, so `ui/Items` updates through `References.ITEM_STACK` directly.

## Cloud workspace pitfalls

- The device shell has Java 11 and **no network**; the cloud container can't reach Maven, Paper or Fabric either,
  but `apt-get install openjdk-25-jdk-headless` works there. What worked (2026-11-16): on the device, tar
  `blueprints-mc\.tools\gradle-home\caches\modules-2\files-2.1` **and** `metadata-2.107` (1.3 MB, Gradle's resolved
  metadata; without it many POMs are missing) plus `wrapper\dists\gradle-9.8.0-bin`, and the Paper server bits
  (`paper-26.3-211.jar`, `cache/`, `versions/`, `libraries/` from `blueprints-mc\run\local-paper`) into
  `item-beacon\.tools\stage\` (gitignored, each under 400 MB), stage them, unpack into a `GRADLE_USER_HOME` in the
  container, and run the distribution's `gradle --offline` directly (no init script needed). Delete `.tools\stage\`
  afterwards.
- The mod also needs `caches/fabric-loom/26.3`, `minecraftMaven` and `mojang_versions_manifest.json` (one 324 MB tar)
  unpacked into the same `GRADLE_USER_HOME`; Loom 1.19.2 needs Gradle itself to run on JDK 25 (`JAVA_HOME`). Its unit
  tests need Linux natives the owner's cache lacks: run `ci/client-pure-tests` (JUnit 6.1.3 is the cached version).
- The owner may have saved the "offline-mc-build" skill: it describes this whole routine.
- Getting commits to the owner: `git bundle create X.bundle main` (later `<his HEAD>..main`), copy it to
  `.tools\transfer\`, `git pull --ff-only .tools/transfer/X.bundle main` in the repo, delete `.tools/transfer`. Git
  on the device needs delete permission (its lock files).
