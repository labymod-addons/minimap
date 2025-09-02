package net.labymod.addons.minimap.server;

import java.util.UUID;
import net.labymod.serverapi.api.packet.PacketHandler;
import org.jetbrains.annotations.NotNull;

public class MinimapPacketHandler implements PacketHandler<MinimapPacket> {

  private final MinimapServers servers;

  public MinimapPacketHandler(MinimapServers servers) {
    this.servers = servers;
  }

  @Override
  public void handle(@NotNull UUID sender, @NotNull MinimapPacket packet) {
    this.servers.setCurrentlyAllowed(packet.isAllowed());
  }
}
