package net.labymod.addons.minimap.server;

import java.util.List;
import java.util.UUID;
import net.labymod.serverapi.api.packet.Packet;
import net.labymod.serverapi.api.payload.io.PayloadReader;
import net.labymod.serverapi.api.payload.io.PayloadWriter;
import org.jetbrains.annotations.NotNull;

/**
 * Player positions, so the world map can show players outside the client's render distance.
 * Every packet holds the full list and replaces the previous one. A player missing from it is
 * gone. Only send players of the receiving client's world. The dimension id, like
 * {@code minecraft:the_nether}, tells its dimensions apart.
 */
public class MinimapPlayersPacket implements Packet {

  private List<Entry> players = List.of();

  public MinimapPlayersPacket() {
  }

  public MinimapPlayersPacket(List<Entry> players) {
    this.players = players;
  }

  @Override
  public void read(@NotNull PayloadReader reader) {
    this.players = reader.readList(() -> new Entry(
        reader.readUUID(),
        reader.readString(),
        reader.readString(),
        reader.readDouble(),
        reader.readDouble()
    ));
  }

  @Override
  public void write(@NotNull PayloadWriter writer) {
    writer.writeCollection(this.players, entry -> {
      writer.writeUUID(entry.uuid());
      writer.writeString(entry.name());
      writer.writeString(entry.dimension());
      writer.writeDouble(entry.x());
      writer.writeDouble(entry.z());
    });
  }

  public List<Entry> players() {
    return this.players;
  }

  public record Entry(UUID uuid, String name, String dimension, double x, double z) {

  }
}
