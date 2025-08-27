package net.labymod.addons.minimap.config;

import net.labymod.addons.minimap.api.config.MinimapConfig;
import net.labymod.addons.minimap.api.map.MinimapCardinalType;
import net.labymod.addons.minimap.api.map.MinimapDisplayType;
import net.labymod.addons.minimap.api.map.MinimapPlayerIcon;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.color.ColorPickerWidget.ColorPickerSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownEntryTranslationPrefix;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingSection;
import net.labymod.api.util.Color;

public class MinimapConfiguration extends AddonConfig implements MinimapConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @DropdownEntryTranslationPrefix("labysminimap.hudWidget.minimap.displayType.entries")
  @DropdownSetting
  private final ConfigProperty<MinimapDisplayType> displayType = ConfigProperty.createEnum(
      MinimapDisplayType.ROUND
  );

  @SwitchSetting
  private final ConfigProperty<Boolean> jumpBouncing = ConfigProperty.create(false);

  @SwitchSetting
  private final ConfigProperty<Boolean> autoZoom = ConfigProperty.create(false);

  @SliderSetting(min = 2, max = 30)
  private final ConfigProperty<Integer> zoom = ConfigProperty.create(12);

  @SliderSetting(min = 2, max = 30)
  private final ConfigProperty<Integer> tileSize = ConfigProperty.create(12);

  @DropdownSetting
  @DropdownEntryTranslationPrefix("labysminimap.hudWidget.minimap.cardinalType.entries")
  private final ConfigProperty<MinimapCardinalType> cardinalType = ConfigProperty.create(MinimapCardinalType.NORMAL);

  @SettingSection("player")
  @DropdownSetting
  private final ConfigProperty<MinimapPlayerIcon> playerIcon = ConfigProperty.create(MinimapPlayerIcon.TRIANGLE);

  @ColorPickerSetting(alpha = true, chroma = true)
  private final ConfigProperty<Color> playerColor  = ConfigProperty.create(Color.WHITE);

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  @Override
  public ConfigProperty<MinimapDisplayType> displayType() {
    return this.displayType;
  }

  @Override
  public ConfigProperty<Boolean> jumpBouncing() {
    return this.jumpBouncing;
  }

  @Override
  public ConfigProperty<Boolean> autoZoom() {
    return this.autoZoom;
  }

  @Override
  public ConfigProperty<Integer> zoom() {
    return this.zoom;
  }

  @Override
  public ConfigProperty<Integer> tileSize() {
    return this.tileSize;
  }

  @Override
  public ConfigProperty<MinimapCardinalType> cardinalType() {
    return this.cardinalType;
  }

  @Override
  public ConfigProperty<MinimapPlayerIcon> playerIcon() {
    return this.playerIcon;
  }

  @Override
  public ConfigProperty<Color> playerColor() {
    return this.playerColor;
  }
  
}
