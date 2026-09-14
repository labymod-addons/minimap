package net.labymod.addons.minimap.world;

import java.util.List;

/**
 * Waypoints on the world map. The waypoints integration sets this while that addon is enabled.
 */
public interface WorldMapWaypoints {

  List<WorldMapWaypoint> waypoints(MapWorldKey key);

  void create(MapWorldKey key, int x, int y, int z);

  void edit(String id);

  void hide(String id);

  void move(String id, double x, double y, double z);
}
