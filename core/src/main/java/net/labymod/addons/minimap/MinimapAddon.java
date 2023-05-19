package net.labymod.addons.minimap;

import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.models.addon.annotation.AddonMain;

@AddonMain
public class MinimapAddon extends LabyAddon<MinimapConfiguration> {

  @Override
  protected void enable() {
    Laby.references().hudWidgetRegistry().register(new MinimapHudWidget());
  }

  @Override
  protected Class<? extends MinimapConfiguration> configurationClass() {
    return MinimapConfiguration.class;
  }
}
