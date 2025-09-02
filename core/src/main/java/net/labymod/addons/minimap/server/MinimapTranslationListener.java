package net.labymod.addons.minimap.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.labymod.api.serverapi.KeyedTranslationListener;
import net.labymod.serverapi.api.packet.Packet;

public class MinimapTranslationListener extends KeyedTranslationListener {

  private static final String ADDONS_KEY = "addons";

  protected MinimapTranslationListener() {
    super(ADDONS_KEY);
  }

  @Override
  protected Packet translateIncomingMessage(JsonElement element) {
    if (!element.isJsonObject()) {
      return null;
    }

    JsonObject object = element.getAsJsonObject();
    if (!object.has("minimap")) {
      return null;
    }

    JsonObject minimap = object.getAsJsonObject("minimap");

    MinimapPacket packet = new MinimapPacket();

    packet.setAllowed(!minimap.has("allowed") || minimap.get("allowed").getAsBoolean());
    packet.setFairplay(!minimap.has("fairplay") || minimap.get("fairplay").getAsBoolean());

    return packet;
  }

  @Override
  protected JsonElement translateOutgoingMessage(Packet packet) {
    return null;
  }
}
