package net.labymod.addons.minimap;

import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.network.server.ServerJoinEvent;
import net.labymod.api.event.client.world.WorldEnterEvent;
import net.labymod.api.event.client.world.WorldEnterEvent.Type;
import java.util.Locale;

public class MinimapServers {

  private final static String[] SERVERS = {"hypixel", "mineplex", "rewinside", "timolia", "hivemc"};

  private boolean currentlyAllowed = true;

  public boolean isAllowed(String address) {
    String lowerAddress = address.toLowerCase(Locale.US);
    for (String server : SERVERS) {
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
}
