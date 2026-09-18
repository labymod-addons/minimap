package net.labymod.addons.minimap.server;

import java.util.List;
import net.labymod.serverapi.api.model.component.ServerAPIComponent;
import net.labymod.serverapi.api.packet.Packet;
import net.labymod.serverapi.api.payload.io.PayloadReader;
import net.labymod.serverapi.api.payload.io.PayloadWriter;
import org.jetbrains.annotations.NotNull;

/**
 * Actions the server offers for players on the world map, like teleporting to them. Every packet
 * replaces the previous list, an empty one removes them. Choosing an action sends a
 * {@link MinimapPlayerActionPacket} back.
 */
public class MinimapPlayerActionsPacket implements Packet {

  private List<Action> actions = List.of();

  public MinimapPlayerActionsPacket() {
  }

  public MinimapPlayerActionsPacket(List<Action> actions) {
    this.actions = actions;
  }

  @Override
  public void read(@NotNull PayloadReader reader) {
    this.actions = reader.readList(() -> new Action(reader.readString(), reader.readComponent()));
  }

  @Override
  public void write(@NotNull PayloadWriter writer) {
    writer.writeCollection(this.actions, action -> {
      writer.writeString(action.id());
      writer.writeComponent(action.name());
    });
  }

  public List<Action> actions() {
    return this.actions;
  }

  /**
   * @param id   sent back when the player chooses the action
   * @param name shown in the menus
   */
  public record Action(String id, ServerAPIComponent name) {

  }
}
