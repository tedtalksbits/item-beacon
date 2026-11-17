package dev.whizzle.itembeacon;

import dev.whizzle.itembeacon.model.Protocol;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ClientBridgeTest {
    record Sent(Player player, String channel, byte[] data) {}

    ServerMock server;
    List<Sent> sent;
    ClientBridge bridge;
    Waypoints waypoints;

    @BeforeEach void start(@TempDir Path dir) {
        server = MockBukkit.mock();
        sent = new ArrayList<>();
        Logger log = Logger.getLogger("test");
        Beacons beacons = new Beacons(log);
        Search search = new Search(new Index(dir, log), beacons, 48);
        waypoints = new Waypoints(MockBukkit.createMockPlugin(), 600_000, (p, id) -> bridge.opened(p, id));
        bridge = new ClientBridge((p, c, d) -> sent.add(new Sent(p, c, d)), ClientBridge.real(beacons), search, waypoints);
    }

    @AfterEach void stop() { MockBukkit.unmock(); }

    @Test void helloGetsWelcomeWithRadiusAndWaypointLifetime() {
        Player p = server.addPlayer();
        bridge.onPluginMessageReceived(ClientBridge.HELLO, p, Protocol.hello());
        assertEquals(1, sent.size());
        assertEquals(ClientBridge.WELCOME, sent.getFirst().channel());
        assertEquals(new Protocol.Welcome(Protocol.VERSION, 48, 600), Protocol.readWelcome(sent.getFirst().data()));
        assertTrue(bridge.modded(p));
    }

    @Test void anotherProtocolVersionIsWelcomedButNotModded() {
        Player p = server.addPlayer();
        bridge.onPluginMessageReceived(ClientBridge.HELLO, p, new dev.whizzle.itembeacon.model.Wire.Writer().varInt(Protocol.VERSION + 1).bytes());
        assertEquals(1, sent.size());
        assertFalse(bridge.modded(p));
    }

    @Test void openedReachesOnlyModdedPlayers() {
        Player modded = server.addPlayer(), plain = server.addPlayer();
        bridge.onPluginMessageReceived(ClientBridge.HELLO, modded, Protocol.hello());
        sent.clear();
        bridge.opened(plain, "b:x");
        bridge.opened(modded, "b:y");
        assertEquals(1, sent.size());
        assertEquals("b:y", Protocol.readOpened(sent.getFirst().data()));
    }

    @Test void pickWithoutASessionOrMalformedIsIgnored() {
        Player p = server.addPlayer();
        bridge.onPluginMessageReceived(ClientBridge.PICK, p, Protocol.pick(1, 0));
        bridge.onPluginMessageReceived(ClientBridge.PICK, p, new byte[] {(byte) 0xFF});
        assertTrue(sent.isEmpty());
    }

    @Test void showWithoutASessionIsIgnoredAndClearAnswersNothing() {
        Player p = server.addPlayer();
        bridge.onPluginMessageReceived(ClientBridge.SHOW, p, Protocol.show(1, 0, false, true));
        bridge.onPluginMessageReceived(ClientBridge.CLEAR, p, Protocol.clear());
        bridge.onPluginMessageReceived(ClientBridge.CLEAR, p, new byte[] {1});
        assertTrue(sent.isEmpty());
        assertTrue(waypoints.of(p).isEmpty());
    }

    @Test void tooManyMessagesPerSecondAreDropped() {
        Player p = server.addPlayer();
        bridge.clock = () -> 5;
        for (int i = 0; i < 100; i++) bridge.onPluginMessageReceived(ClientBridge.HELLO, p, Protocol.hello());
        assertEquals(ClientBridge.MESSAGES_PER_SECOND, sent.size());
    }
}
