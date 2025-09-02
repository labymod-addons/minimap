package net.labymod.addons.minimap.api.config;

import net.labymod.api.reference.annotation.Referenceable;

@Referenceable
public interface MinimapConfigProvider {

  MinimapConfig config();

  MinimapHudWidgetConfig hudWidgetConfig();

}
