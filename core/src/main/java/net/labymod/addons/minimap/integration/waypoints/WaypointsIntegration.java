package net.labymod.addons.minimap.integration.waypoints;

import java.util.ArrayList;
import java.util.List;
import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.waypoints.Waypoints;
import net.labymod.addons.waypoints.waypoint.Waypoint;
import net.labymod.addons.waypoints.waypoint.WaypointType;
import net.labymod.api.Laby;
import net.labymod.api.addon.integration.AddonIntegration;
import net.labymod.api.client.component.Component;
import net.labymod.api.util.Color;
import net.labymod.api.util.math.vector.FloatVector3;

public class WaypointsIntegration implements AddonIntegration {

  private final List<WaypointContainer> waypointContainers = new ArrayList<>();

  @Override
  public void load() {
  }

  @Override
  public void onIntegratedAddonEnable() {
    Laby.labyAPI().eventBus().registerListener(this);
    MinimapAddon.getReferences().tileRendererDispatcher().register(
        configProvider -> new WaypointsTileRenderer(this, configProvider));
  }

  @Override
  public void onIntegratedAddonDisable() {
    Laby.labyAPI().eventBus().unregisterListener(this);
  }

  public void onRefreshWaypoints() {
    this.waypointContainers.clear();
    for (var visibleWaypoint : Waypoints.getReferences().waypointService().getVisibleWaypoints()) {
      this.waypointContainers.add(new WaypointContainer(visibleWaypoint));
    }
  }

  public List<WaypointContainer> getWaypoints() {
    return this.waypointContainers;
  }

  public record WaypointContainer(Waypoint waypoint, FloatVector3 position) {

    public WaypointContainer(Waypoint waypoint) {
      this(waypoint, waypoint.location().copy());
    }

    public WaypointType type() {
      return this.waypoint.type();
    }

    public Color color() {
      return this.waypoint.color();
    }

    public Component title() {
      return this.waypoint.title();
    }
  }

}
