package net.labymod.addons.minimap.server;

import java.util.Locale;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.Laby;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.network.server.ServerJoinEvent;
import net.labymod.api.event.client.world.WorldEnterEvent;
import net.labymod.api.event.client.world.WorldEnterEvent.Type;
import net.labymod.api.serverapi.LabyModProtocolService;
import net.labymod.api.serverapi.TranslationProtocol;
import net.labymod.serverapi.api.ProtocolRegistry;
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


  private boolean currentlyAllowed = true;

  public void init() {
    Laby.labyAPI().eventBus().registerListener(this);

    LabyModProtocolService protocolService = Laby.references().labyModProtocolService();
    ProtocolRegistry registry = protocolService.registry();
    AddonProtocol protocol = new AddonProtocol(protocolService, Util.NAMESPACE);
    registry.registerProtocol(protocol);

    protocol.registerPacket(1, MinimapPacket.class, Direction.BOTH, new MinimapPacketHandler(this));
    TranslationProtocol legacyTranslationProtocol = new TranslationProtocol(LEGACY_ID, protocol);
    legacyTranslationProtocol.registerListener(new MinimapTranslationListener());
    protocolService.translationRegistry().register(legacyTranslationProtocol);
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

  public boolean isCurrentlyAllowed() {
    return this.currentlyAllowed;
  }

  public void setCurrentlyAllowed(boolean currentlyAllowed) {
    this.currentlyAllowed = currentlyAllowed;
  }
}
