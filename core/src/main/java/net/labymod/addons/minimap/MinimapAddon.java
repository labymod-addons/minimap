package net.labymod.addons.minimap;

import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget;
import net.labymod.addons.minimap.server.MinimapPacket;
import net.labymod.addons.minimap.server.MinimapPacketHandler;
import net.labymod.addons.minimap.server.MinimapServers;
import net.labymod.addons.minimap.server.MinimapTranslationListener;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.serverapi.protocol.packet.protocol.AddonProtocol;
import net.labymod.serverapi.protocol.packet.protocol.ProtocolService;

@AddonMain
public class MinimapAddon extends LabyAddon<MinimapConfiguration> {

  private final MinimapServers servers = new MinimapServers();

  @Override
  protected void enable() {
    Laby.references().hudWidgetRegistry().register(new MinimapHudWidget(this));

    this.servers.init();
  }

  @Override
  protected Class<? extends MinimapConfiguration> configurationClass() {
    return MinimapConfiguration.class;
  }

  public boolean isMinimapAllowed() {
    return this.servers.isCurrentlyAllowed();
  }
}
