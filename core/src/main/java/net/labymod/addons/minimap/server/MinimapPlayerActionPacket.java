package net.labymod.addons.minimap.server;

import java.util.UUID;
import net.labymod.serverapi.api.packet.Packet;
import net.labymod.serverapi.api.payload.io.PayloadReader;
import net.labymod.serverapi.api.payload.io.PayloadWriter;
import org.jetbrains.annotations.NotNull;

/**
 * The client sends this when the player picks one of the {@link MinimapPlayerActionsPacket} actions
 * for another player. The server decides what happens and whether it is allowed.
 */
public class MinimapPlayerActionPacket implements Packet {

  private String action;
  private UUID target;

  public MinimapPlayerActionPacket() {
  }

  public MinimapPlayerActionPacket(String action, UUID target) {
    this.action = action;
    this.target = target;
  }

  @Override
  public void read(@NotNull PayloadReader reader) {
    this.action = reader.readString();
    this.target = reader.readUUID();
  }

  @Override
  public void write(@NotNull PayloadWriter writer) {
    writer.writeString(this.action);
    writer.writeUUID(this.target);
  }

  public String action() {
    return this.action;
  }

  public UUID target() {
    return this.target;
  }
}
