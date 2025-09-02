package net.labymod.addons.minimap.server;

import net.labymod.serverapi.api.packet.Packet;
import net.labymod.serverapi.api.payload.io.PayloadReader;
import net.labymod.serverapi.api.payload.io.PayloadWriter;
import org.jetbrains.annotations.NotNull;

public class MinimapPacket implements Packet {

  private boolean allowed;
  private boolean fairplay;

  @Override
  public void read(@NotNull PayloadReader reader) {
    this.setAllowed(reader.readBoolean());
    this.setFairplay(reader.readBoolean());
  }

  @Override
  public void write(@NotNull PayloadWriter writer) {
    writer.writeBoolean(this.isAllowed());
    writer.writeBoolean(this.isFairplay());
  }

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
