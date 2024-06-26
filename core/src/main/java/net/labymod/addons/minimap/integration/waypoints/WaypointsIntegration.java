package net.labymod.addons.minimap.integration.waypoints;

import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.api.Laby;
import net.labymod.api.addon.integration.AddonIntegration;

public class WaypointsIntegration implements AddonIntegration {

  @Override
  public void load() {
  }

  @Override
  public void onIntegratedAddonEnable() {
    Laby.labyAPI().eventBus().registerListener(this);
    MinimapAddon.getReferences().tileRendererDispatcher().register(WaypointsTileRenderer::new);
  }

  @Override
  public void onIntegratedAddonDisable() {
    Laby.labyAPI().eventBus().unregisterListener(this);
  }

}
