package net.labymod.addons.minimap.config;

import net.labymod.addons.minimap.api.config.MinimapConfig;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.property.ConfigProperty;

public class MinimapConfiguration extends AddonConfig implements MinimapConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  @Override
  public boolean isEnabled() {
    return this.enabled().get();
  }
}
