package net.labymod.addons.minimap.api;

import net.labymod.addons.minimap.api.map.MinimapCardinalType;
import net.labymod.addons.minimap.api.map.MinimapDisplayType;
import net.labymod.addons.minimap.api.map.MinimapUpdateMethod;
import net.labymod.api.client.gui.hud.hudwidget.HudWidgetConfig;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownEntryTranslationPrefix;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.configuration.loader.property.ConfigProperty;

public class MinimapHudWidgetConfig extends HudWidgetConfig {

  public static final float BORDER_PADDING = 5F;

  @DropdownEntryTranslationPrefix("labysminimap.hudWidget.minimap.displayType.entries")
  @DropdownSetting
  private final ConfigProperty<MinimapDisplayType> displayType =
      new ConfigProperty<>(MinimapDisplayType.ROUND);
  @SwitchSetting
  private final ConfigProperty<Boolean> jumpBouncing = new ConfigProperty<>(false);
  @SwitchSetting
  private final ConfigProperty<Boolean> autoZoom = new ConfigProperty<>(false);
  @SliderSetting(min = 2, max = 30)
  private final ConfigProperty<Integer> zoom = new ConfigProperty<>(12);
  @SliderSetting(min = 2, max = 30)
  private final ConfigProperty<Integer> tileSize = new ConfigProperty<>(12);
  @DropdownSetting
  @DropdownEntryTranslationPrefix("labysminimap.hudWidget.minimap.updateMethod.entries")
  private final ConfigProperty<MinimapUpdateMethod> updateMethod =
      new ConfigProperty<>(MinimapUpdateMethod.CHUNK_TRIGGER);
  @DropdownSetting
  @DropdownEntryTranslationPrefix("labysminimap.hudWidget.minimap.cardinalType.entries")
  private final ConfigProperty<MinimapCardinalType> cardinalType
      = new ConfigProperty<>(MinimapCardinalType.NORMAL);

  public ConfigProperty<MinimapDisplayType> displayType() {
    return this.displayType;
  }

  public ConfigProperty<Boolean> jumpBouncing() {
    return this.jumpBouncing;
  }

  public ConfigProperty<Boolean> autoZoom() {
    return this.autoZoom;
  }

  public ConfigProperty<Integer> zoom() {
    return this.zoom;
  }

  public ConfigProperty<MinimapUpdateMethod> updateMethod() {
    return this.updateMethod;
  }

  public ConfigProperty<MinimapCardinalType> cardinalType() {
    return this.cardinalType;
  }

  public ConfigProperty<Integer> tileSize() {
    return this.tileSize;
  }
}