package dev.whizzle.itembeacon.client;

import dev.whizzle.itembeacon.client.net.Net;
import dev.whizzle.itembeacon.client.render.BeaconModels;
import dev.whizzle.itembeacon.client.ui.PickerScreen;
import dev.whizzle.itembeacon.model.Protocol;
import dev.whizzle.itembeacon.model.Protocol.ChunkPos;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Item Beacon client. Talks to the plugin: hello; from then on each chunk's beacons arrive right behind the chunk
 * (for the model swap, {@code render/}); right-clicking a beacon sends the summary, which opens the picker
 * ({@link PickerScreen}). The beacon item needs no code: {@code assets/minecraft/items/lodestone.json} selects the
 * beacon model by the item's custom model data. {@code /itembeaconmod} shows what the mod knows.
 */
public final class ItemBeaconClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("itembeacon");
    public static final BeaconCache BEACONS = new BeaconCache();
    private static final SummaryAssembler SUMMARIES = new SummaryAssembler();
    /** From the server's welcome; 0 until it answers (or when the server doesn't have the plugin). */
    static int serverProtocol;
    public static int radius = 64, waypointSeconds = 600;
    private static final Set<Long> LOADED = new HashSet<>();
    private static final LinkedHashSet<Long> TO_ASK = new LinkedHashSet<>();

    @Override
    public void onInitializeClient() {
        Net.register(Map.of(
            Protocol.WELCOME, data -> {
                Protocol.Welcome w = Protocol.readWelcome(data);
                serverProtocol = w.version();
                radius = w.radius();
                waypointSeconds = w.waypointSeconds();
                if (serverProtocol != Protocol.VERSION) LOG.warn("Server speaks Item Beacon protocol {}, this mod {}: the mod stays off", serverProtocol, Protocol.VERSION);
                else TO_ASK.addAll(LOADED);
            },
            Protocol.CHUNK, data -> onChunk(Protocol.readChunk(data)),
            Protocol.SUMMARY, data -> onSummary(Protocol.readSummary(data)),
            Protocol.PLACES, data -> {
                Protocol.Places p = Protocol.readPlaces(data);
                if (Minecraft.getInstance().gui.screen() instanceof PickerScreen s) s.places(p);
            },
            Protocol.OPENED, data -> Waypoints.opened(Protocol.readOpened(data))));
        BeaconModels.register();
        CreativeTab.register();
        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> reset());
        ServerboundPlayChannelEvents.REGISTER.register((listener, sender, client, channels) -> {
            if (channels.contains(Identifier.fromNamespaceAndPath(Protocol.NAMESPACE, Protocol.HELLO))) Net.send(Protocol.HELLO, Protocol.hello());
        });
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> { BEACONS.clear(); LOADED.clear(); TO_ASK.clear(); Waypoints.clear(); });
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> LOADED.add(new ChunkPos(chunk.getPos().x(), chunk.getPos().z()).key()));
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            ChunkPos pos = new ChunkPos(chunk.getPos().x(), chunk.getPos().z());
            LOADED.remove(pos.key());
            TO_ASK.remove(pos.key());
            BEACONS.forget(pos);
        });
        ClientTickEvents.END_CLIENT_TICK.register(ItemBeaconClient::tick);
        // Right-clicking a beacon is a use, like a chest: without this the game sees a plain lodestone, places the held
        // block itself (sound, subtitle, a flash) and the server takes it back. SUCCESS sends the click to the server
        // (Fabric's MultiPlayerGameModeMixin) without placing anything here.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || !active()) return InteractionResult.PASS;
            var pos = hit.getBlockPos();
            if (!BEACONS.at(pos.getX(), pos.getY(), pos.getZ()) || !level.getBlockState(pos).is(Blocks.LODESTONE)) return InteractionResult.PASS;
            ItemStack held = player.getItemInHand(hand);
            return UseRule.beaconUse(player.isSecondaryUseActive(), held.isEmpty(), held.is(Items.COMPASS)) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) ->
            dispatcher.register(ClientCommands.literal("itembeaconmod").executes(c -> { status(c.getSource().getClient()); return 1; })));
    }

    static void reset() { serverProtocol = 0; BEACONS.clear(); LOADED.clear(); TO_ASK.clear(); Waypoints.clear(); }

    public static boolean active() { return serverProtocol == Protocol.VERSION; }

    static void tick(Minecraft mc) {
        Waypoints.tick();
        if (!active() || mc.level == null || TO_ASK.isEmpty()) return;
        List<ChunkPos> batch = new ArrayList<>(Protocol.MAX_QUERY);
        for (Iterator<Long> it = TO_ASK.iterator(); it.hasNext() && batch.size() < Protocol.MAX_QUERY; ) {
            batch.add(ChunkPos.of(it.next()));
            it.remove();
        }
        Net.send(Protocol.QUERY, Protocol.query(batch));
    }

    static void onChunk(Protocol.Chunk chunk) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !LOADED.contains(chunk.pos().key())) return;
        TO_ASK.remove(chunk.pos().key());
        for (int sy : BEACONS.update(chunk.pos(), chunk.beacons())) level.setSectionDirtyWithNeighbors(chunk.pos().x(), sy, chunk.pos().z());
    }

    /** A page of the summary; the last one opens the picker. */
    static void onSummary(Protocol.SummaryPage page) {
        SummaryAssembler.Summary s = SUMMARIES.accept(page);
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        mc.gui.setScreen(new PickerScreen(s));
    }

    static void status(Minecraft mc) {
        if (mc.player == null) return;
        if (serverProtocol == 0) { mc.player.sendSystemMessage(Component.literal("Item Beacon: this server doesn't run the plugin (no answer).")); return; }
        if (!active()) { mc.player.sendSystemMessage(Component.literal("Item Beacon: server protocol " + serverProtocol + ", mod " + Protocol.VERSION + ". Update the older one.")); return; }
        mc.player.sendSystemMessage(Component.literal("Item Beacon: connected (radius " + radius + ", " + BEACONS.size() + " beacons known nearby, "
            + Waypoints.ids().size() + " waypoints, " + TO_ASK.size() + " chunks still to ask about)."));
    }
}
