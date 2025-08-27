package net.labymod.addons.minimap.api.config;

import net.labymod.addons.minimap.api.map.MinimapCardinalType;
import net.labymod.addons.minimap.api.map.MinimapDisplayType;
import net.labymod.addons.minimap.api.map.MinimapPlayerIcon;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.util.Color;

public interface MinimapConfig {

  ConfigProperty<MinimapDisplayType> displayType();

  ConfigProperty<Boolean> jumpBouncing();

  ConfigProperty<Boolean> autoZoom();

  ConfigProperty<Integer> zoom();

  ConfigProperty<Integer> tileSize();

  ConfigProperty<MinimapCardinalType> cardinalType();

  ConfigProperty<MinimapPlayerIcon> playerIcon();

  ConfigProperty<Color> playerColor();
}
