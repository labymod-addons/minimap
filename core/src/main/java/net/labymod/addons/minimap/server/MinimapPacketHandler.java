package net.labymod.addons.minimap.server;

import net.labymod.serverapi.protocol.packet.PacketHandler;

public class MinimapPacketHandler implements PacketHandler<MinimapPacket> {

  private final MinimapServers servers;

  public MinimapPacketHandler(MinimapServers servers) {
    this.servers = servers;
  }

  @Override
  public void handle(MinimapPacket packet) {
    this.servers.setCurrentlyAllowed(packet.isAllowed());
  }
}
