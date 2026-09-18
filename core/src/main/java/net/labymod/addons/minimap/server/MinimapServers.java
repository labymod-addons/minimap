package net.labymod.addons.minimap.server;

import java.util.Locale;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.Laby;
import net.labymod.api.client.network.server.ServerData;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.network.server.ServerJoinEvent;
import net.labymod.api.event.client.world.WorldEnterEvent;
import net.labymod.api.event.client.world.WorldEnterEvent.Type;
import net.labymod.api.serverapi.LabyModProtocolService;
import net.labymod.api.serverapi.TranslationProtocol;
import net.labymod.serverapi.api.ProtocolRegistry;
import net.labymod.serverapi.api.packet.Packet;
import net.labymod.serverapi.api.packet.Direction;
import net.labymod.serverapi.api.payload.PayloadChannelIdentifier;
import net.labymod.serverapi.core.AddonProtocol;

public class MinimapServers {

  private static final PayloadChannelIdentifier LEGACY_ID = PayloadChannelIdentifier.create("labymod3", "main");
  private final static String[] BLACKLIST = {
      "hypixel",
      "mineplex",
      "rewinside",
      "timolia",
      "hivemc"
  };


  private final RemotePlayers remotePlayers = new RemotePlayers(this);
  private boolean currentlyAllowed = true;
  private LabyModProtocolService protocolService;
  private AddonProtocol protocol;

  public void init() {
    Laby.labyAPI().eventBus().registerListener(this);
    Laby.labyAPI().eventBus().registerListener(this.remotePlayers);

    LabyModProtocolService protocolService = Laby.references().labyModProtocolService();
    ProtocolRegistry registry = protocolService.registry();
    AddonProtocol protocol = new AddonProtocol(protocolService, Util.NAMESPACE);
    registry.registerProtocol(protocol);
    this.protocolService = protocolService;
    this.protocol = protocol;

    protocol.registerPacket(1, MinimapPacket.class, Direction.BOTH, new MinimapPacketHandler(this));
    protocol.registerPacket(
        2,
        MinimapPlayersPacket.class,
        Direction.CLIENTBOUND,
        new MinimapPlayersPacketHandler(this.remotePlayers)
    );
    protocol.registerPacket(
        3,
        MinimapPlayerActionsPacket.class,
        Direction.CLIENTBOUND,
        new MinimapPlayerActionsPacketHandler(this.remotePlayers)
    );
    protocol.registerPacket(4, MinimapPlayerActionPacket.class, Direction.SERVERBOUND);
    TranslationProtocol legacyTranslationProtocol = new TranslationProtocol(LEGACY_ID, protocol);
    legacyTranslationProtocol.registerListener(new MinimapTranslationListener());
    protocolService.translationRegistry().register(legacyTranslationProtocol);
  }

  public void refreshAllowedState() {
    ServerData serverData = Laby.labyAPI().serverController().getCurrentServerData();
    this.currentlyAllowed = serverData == null || this.isAllowed(serverData.address().getHost());
  }

  public boolean isAllowed(String address) {
    String lowerAddress = address.toLowerCase(Locale.US);
    for (String server : BLACKLIST) {
      if (lowerAddress.contains(server)) {
        return false;
      }
    }

    return true;
  }

  @Subscribe
  public void updateAllowedState(ServerJoinEvent event) {
    String host = event.serverData().address().getHost();

    this.currentlyAllowed = this.isAllowed(host);
  }

  @Subscribe
  public void updateAllowedState(WorldEnterEvent event) {
    if (event.type() == Type.SINGLEPLAYER) {
      this.currentlyAllowed = true;
    }
  }

  @Subscribe
  public void updateAllowedState(ServerDisconnectEvent event) {
    this.currentlyAllowed = true;
  }

  void send(Packet packet) {
    this.protocolService.send(this.protocol, packet);
  }

  public RemotePlayers remotePlayers() {
    return this.remotePlayers;
  }

  public boolean isCurrentlyAllowed() {
    return this.currentlyAllowed;
  }

  public void setCurrentlyAllowed(boolean currentlyAllowed) {
    this.currentlyAllowed = currentlyAllowed;
  }
}
