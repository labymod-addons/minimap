package net.labymod.addons.minimap.integration.waypoints;

import java.util.ArrayList;
import java.util.List;
import net.labymod.addons.minimap.world.MapWorldKey;
import net.labymod.addons.minimap.world.WorldMapWaypoint;
import net.labymod.addons.minimap.world.WorldMapWaypoints;
import net.labymod.addons.waypoints.WaypointService;
import net.labymod.addons.waypoints.Waypoints;
import net.labymod.addons.waypoints.core.activity.popup.ManageWaypointSimplePopup;
import net.labymod.addons.waypoints.waypoint.Waypoint;
import net.labymod.addons.waypoints.waypoint.WaypointBuilder;
import net.labymod.addons.waypoints.waypoint.WaypointContext;
import net.labymod.addons.waypoints.waypoint.WaypointMeta;
import net.labymod.addons.waypoints.waypoint.WaypointType;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.network.server.ServerAddress;
import net.labymod.api.util.I18n;
import net.labymod.api.util.math.vector.DoubleVector3;
import org.jetbrains.annotations.Nullable;

public class WaypointsWorldMap implements WorldMapWaypoints {

  private static final String DEFAULT_TITLE = "New Waypoint";

  private final List<WorldMapWaypoint> waypoints = new ArrayList<>();
  @Nullable
  private MapWorldKey cachedKey;

  public void invalidate() {
    this.cachedKey = null;
  }

  @Override
  public List<WorldMapWaypoint> waypoints(MapWorldKey key) {
    if (key.equals(this.cachedKey)) {
      return this.waypoints;
    }

    this.waypoints.clear();
    this.cachedKey = key;
    WaypointContext context = key.multiplayer()
        ? WaypointContext.MULTI_PLAYER
        : WaypointContext.SINGLE_PLAYER;
    for (Waypoint waypoint : Waypoints.references().waypointService().getAll()) {
      WaypointMeta meta = waypoint.meta();
      if (!meta.isVisible()
          || (meta.contextType() != null && !meta.matchesContext(context, key.context()))) {
        continue;
      }

      String dimension = meta.getDimension();
      if (dimension != null && !dimension.equals(key.dimension())) {
        continue;
      }

      DoubleVector3 location = meta.location();
      this.waypoints.add(new WorldMapWaypoint(
          meta.getIdentifier(),
          location.getX(), location.getY(), location.getZ(),
          meta.icon(),
          meta.iconColor(),
          meta.title()
      ));
    }

    return this.waypoints;
  }

  /**
   * Adds the waypoint right away and opens its edit dialog. The dialog's add mode always uses the
   * player position, and a subclass would lose the waypoints addon's styles.
   */
  @Override
  public void create(MapWorldKey key, int x, int y, int z) {
    String title = I18n.getTranslation("labyswaypoints.defaultName");
    WaypointBuilder builder = WaypointBuilder.create()
        .title(Component.text(title == null ? DEFAULT_TITLE : title))
        .type(WaypointType.PERMANENT)
        .location(new DoubleVector3(x + 0.5D, y, z + 0.5D))
        .dimension(key.dimension());
    builder = key.multiplayer()
        ? builder.server(ServerAddress.parse(key.context()))
        : builder.singlePlayer(key.context());

    WaypointService service = Waypoints.references().waypointService();
    Waypoint waypoint = service.add(builder.build());
    service.refresh();
    if (waypoint != null) {
      new ManageWaypointSimplePopup(waypoint.meta()).displayInOverlay();
    }
  }

  @Override
  public void edit(String id) {
    Waypoint waypoint = Waypoints.references().waypointService().get(id);
    if (waypoint != null) {
      new ManageWaypointSimplePopup(waypoint).displayInOverlay();
    }
  }

  @Override
  public void hide(String id) {
    WaypointService service = Waypoints.references().waypointService();
    Waypoint waypoint = service.get(id);
    if (waypoint == null) {
      return;
    }

    WaypointMeta meta = waypoint.meta().copy();
    meta.setVisible(false);
    service.update(meta);
    service.refresh();
  }
}
