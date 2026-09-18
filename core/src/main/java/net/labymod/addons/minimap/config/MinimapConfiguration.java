package net.labymod.addons.minimap.config;

import net.labymod.addons.minimap.api.config.MinimapConfig;
import net.labymod.addons.minimap.world.MapMode;
import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.widget.widgets.input.KeybindWidget.KeyBindSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.property.ConfigProperty;

public class MinimapConfiguration extends AddonConfig implements MinimapConfig {

  @SwitchSetting
  private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);

  @KeyBindSetting
  private final ConfigProperty<Key> worldMapKey = new ConfigProperty<>(Key.J);

  @SliderSetting(min = 0, max = 7)
  private final ConfigProperty<Integer> biomeBlend = new ConfigProperty<>(2);

  private final ConfigProperty<Boolean> worldMapAtlasOpen = new ConfigProperty<>(false);

  private final ConfigProperty<Boolean> worldMapChunkGrid = new ConfigProperty<>(false);

  private final ConfigProperty<Boolean> worldMapCaveLayer = new ConfigProperty<>(false);

  private final ConfigProperty<Boolean> worldMapEntities = new ConfigProperty<>(true);

  private final ConfigProperty<MapMode> worldMapMode = ConfigProperty.createEnum(MapMode.TERRAIN);

  @Override
  public ConfigProperty<Boolean> enabled() {
    return this.enabled;
  }

  public ConfigProperty<Key> worldMapKey() {
    return this.worldMapKey;
  }

  /**
   * Radius in blocks that grass, foliage and the biome map mode average biome colors over.
   */
  public ConfigProperty<Integer> biomeBlend() {
    return this.biomeBlend;
  }

  public ConfigProperty<Boolean> worldMapAtlasOpen() {
    return this.worldMapAtlasOpen;
  }

  public ConfigProperty<Boolean> worldMapChunkGrid() {
    return this.worldMapChunkGrid;
  }

  public ConfigProperty<Boolean> worldMapCaveLayer() {
    return this.worldMapCaveLayer;
  }

  public ConfigProperty<Boolean> worldMapEntities() {
    return this.worldMapEntities;
  }

  public ConfigProperty<MapMode> worldMapMode() {
    return this.worldMapMode;
  }

  @Override
  public boolean isEnabled() {
    return this.enabled().get();
  }
}
