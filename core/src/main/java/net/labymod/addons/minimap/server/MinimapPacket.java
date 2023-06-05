package net.labymod.addons.minimap.server;

import net.labymod.serverapi.protocol.packet.Packet;

public class MinimapPacket implements Packet {

  private boolean allowed;
  private boolean fairplay;

  public boolean isAllowed() {
    return this.allowed;
  }

  public void setAllowed(boolean allowed) {
    this.allowed = allowed;
  }

  public boolean isFairplay() {
    return this.fairplay;
  }

  public void setFairplay(boolean fairplay) {
    this.fairplay = fairplay;
  }
}
