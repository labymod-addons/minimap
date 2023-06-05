package net.labymod.addons.minimap;

import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.addon.annotation.AddonMain;

@AddonMain
public class MinimapAddon extends LabyAddon<MinimapConfiguration> {

  private final MinimapServers servers = new MinimapServers();

  @Override
  protected void enable() {
    Laby.references().hudWidgetRegistry().register(new MinimapHudWidget(this));

    this.registerListener(this.servers);
  }

  @Override
  protected Class<? extends MinimapConfiguration> configurationClass() {
    return MinimapConfiguration.class;
  }

  public boolean isMinimapAllowed() {
    return this.servers.isCurrentlyAllowed();
  }
}
