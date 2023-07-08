package net.labymod.addons.minimap.api;

import net.labymod.api.reference.annotation.Referenceable;

@Referenceable
public interface MinimapConfigProvider {

  MinimapHudWidgetConfig widgetConfig();

}
