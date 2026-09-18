package net.labymod.addons.minimap.server;

import java.util.UUID;
import net.labymod.api.Laby;
import net.labymod.serverapi.api.packet.PacketHandler;
import org.jetbrains.annotations.NotNull;

public class MinimapPlayersPacketHandler implements PacketHandler<MinimapPlayersPacket> {

  private final RemotePlayers players;

  public MinimapPlayersPacketHandler(RemotePlayers players) {
    this.players = players;
  }

  @Override
  public void handle(@NotNull UUID sender, @NotNull MinimapPlayersPacket packet) {
    // Packets arrive on the network thread
    Laby.labyAPI().minecraft().executeOnRenderThread(() -> this.players.update(packet.players()));
  }
}
