package net.labymod.addons.minimap.server;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.labymod.addons.minimap.server.MinimapPlayerActionsPacket.Action;
import net.labymod.addons.minimap.server.RemotePlayers.PlayerAction;
import net.labymod.api.Laby;
import net.labymod.api.serverapi.LabyModProtocolService;
import net.labymod.serverapi.api.packet.PacketHandler;
import org.jetbrains.annotations.NotNull;

public class MinimapPlayerActionsPacketHandler implements PacketHandler<MinimapPlayerActionsPacket> {

  private final RemotePlayers players;

  public MinimapPlayerActionsPacketHandler(RemotePlayers players) {
    this.players = players;
  }

  @Override
  public void handle(@NotNull UUID sender, @NotNull MinimapPlayerActionsPacket packet) {
    LabyModProtocolService protocolService = Laby.references().labyModProtocolService();
    List<PlayerAction> actions = new ArrayList<>();
    for (Action action : packet.actions()) {
      actions.add(new PlayerAction(action.id(), protocolService.mapComponent(action.name())));
    }

    // Packets arrive on the network thread
    Laby.labyAPI().minecraft().executeOnRenderThread(() -> this.players.setActions(actions));
  }
}
