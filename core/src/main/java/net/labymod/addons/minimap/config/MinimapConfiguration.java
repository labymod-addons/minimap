package net.labymod.addons.minimap.config;

import net.labymod.addons.minimap.api.config.MinimapConfig;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.property.ConfigProperty;

public class MinimapConfiguration extends AddonConfig implements MinimapConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @KeyBindSetting
  private final ConfigProperty<Key> worldMapKey = new ConfigProperty<>(Key.J);

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<Key> worldMapKey() {
    return this.worldMapKey;
  }

  @Override
  public boolean isEnabled() {
    return this.enabled().get();
  }
}
