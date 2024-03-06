package net.labymod.addons.minimap.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.labymod.serverapi.protocol.packet.Packet;
import net.labymod.serverapi.protocol.packet.protocol.AddonProtocol;
import net.labymod.serverapi.protocol.packet.protocol.neo.translation.AbstractLabyMod3PayloadTranslationListener;
import net.labymod.serverapi.protocol.payload.identifier.PayloadChannelIdentifier;

public class MinimapTranslationListener extends AbstractLabyMod3PayloadTranslationListener {

  private final AddonProtocol protocol;

  public MinimapTranslationListener(AddonProtocol protocol) {
    super(protocol, "addons");

    this.protocol = protocol;
  }

  @Override
  public byte[] translateIncomingPayload(JsonElement messageContent) {
    if (!messageContent.isJsonObject()) {
      return null;
    }

    JsonObject object = messageContent.getAsJsonObject();
    if (!object.has("minimap")) {
      return null;
    }

    JsonObject minimap = object.getAsJsonObject("minimap");

    MinimapPacket packet = new MinimapPacket();

    packet.setAllowed(!minimap.has("allowed") || minimap.get("allowed").getAsBoolean());
    packet.setFairplay(!minimap.has("fairplay") || minimap.get("fairplay").getAsBoolean());

    return this.writePacketBinary(packet);
  }

  @Override
  public <T extends Packet> byte[] translateOutgoingPayload(T packet) {
    return new byte[0];
  }

}
