package net.labymod.addons.minimap.server;

import net.labymod.api.Laby;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.network.server.ServerJoinEvent;
import net.labymod.api.event.client.world.WorldEnterEvent;
import net.labymod.api.event.client.world.WorldEnterEvent.Type;
import net.labymod.serverapi.protocol.packet.protocol.AddonProtocol;
import net.labymod.serverapi.protocol.packet.protocol.ProtocolService;
import java.util.Locale;

public class MinimapServers {

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

    ProtocolService protocolService = Laby.references().labyProtocolApi().getProtocolService();
    AddonProtocol protocol = new AddonProtocol("labysminimap");

    protocolService.registerAddonProtocol(protocol);

    protocol.registerPacket(1, new MinimapPacket());
    protocolService.registerTranslationListener(new MinimapTranslationListener(protocol));

    protocolService.registerPacketHandler(MinimapPacket.class, new MinimapPacketHandler(this));
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
