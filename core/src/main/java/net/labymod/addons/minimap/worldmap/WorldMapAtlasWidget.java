package net.labymod.addons.minimap.worldmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.world.MapMode;
import net.labymod.addons.minimap.world.MapWorldKey;
import net.labymod.addons.minimap.world.WorldMapService;
import net.labymod.addons.minimap.world.WorldMapWaypoint;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.serializer.plain.PlainTextComponentSerializer;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.action.Switchable;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.gui.screen.widget.context.ContextMenu;
import net.labymod.api.client.gui.screen.widget.context.ContextMenuEntry;
import net.labymod.api.client.gui.screen.widget.cursor.CursorTypes;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.util.I18n;
import net.labymod.api.util.math.position.Position;
import org.jetbrains.annotations.Nullable;

/**
 * Side panel of the world map with dimensions, sub-worlds, view options and the waypoints of the
 * shown dimension. Dimension tabs and sub-world rows have a context menu to manage the saved maps.
 */
@AutoWidget
public final class WorldMapAtlasWidget extends DivWidget {

  private static final String I18N_PREFIX = Util.NAMESPACE + ".worldMap.atlas.";
  private static final String MENU_I18N_PREFIX = I18N_PREFIX + "menu.";
  private static final long MINUTE_MILLIS = 60_000L;
  private static final long HOUR_MILLIS = 60L * MINUTE_MILLIS;
  private static final long DAY_MILLIS = 24L * HOUR_MILLIS;

  private final Actions actions;
  private final WorldMapService service;
  private final List<MapWorldKey> keys;
  private final MapWorldKey viewKey;
  @Nullable
  private final MapWorldKey activeKey;
  private final boolean current;
  private final MapMode mode;
  private final boolean showPlayers;
  private final List<PlayerRow> playerRows = new ArrayList<>();
  private List<WorldMapPlayer> players = List.of();
  @Nullable
  private String playerDimension;
  private double playerX;
  private double playerZ;
  @Nullable
  private ComponentWidget playersLabel;
  @Nullable
  private VerticalListWidget<Widget> playerList;
  @Nullable
  private final List<WorldMapWaypoint> waypoints;
  private final List<WaypointRow> waypointRows = new ArrayList<>();
  private boolean following;
  private boolean caveLayer;
  private boolean chunkGrid;
  private boolean entities;
  private boolean playerHeads;
  private String filter;
  @Nullable
  private WorldMapToggleWidget followToggle;
  @Nullable
  private WorldMapToggleWidget caveToggle;
  @Nullable
  private WorldMapToggleWidget gridToggle;
  @Nullable
  private WorldMapToggleWidget entitiesToggle;
  @Nullable
  private WorldMapToggleWidget playerHeadsToggle;
  @Nullable
  private TextFieldWidget search;

  /**
   * @param current   whether the player is in the shown world
   * @param waypoints the shown dimension's waypoints, {@code null} without the waypoints addon
   */
  public WorldMapAtlasWidget(
      Actions actions,
      WorldMapService service,
      List<MapWorldKey> keys,
      MapWorldKey viewKey,
      boolean current,
      boolean following,
      boolean caveLayer,
      boolean chunkGrid,
      boolean entities,
      boolean playerHeads,
      MapMode mode,
      boolean showPlayers,
      @Nullable List<WorldMapWaypoint> waypoints,
      String filter
  ) {
    this.actions = actions;
    this.service = service;
    this.keys = keys;
    this.viewKey = viewKey;
    this.activeKey = service.activeKey();
    this.current = current;
    this.following = following;
    this.caveLayer = caveLayer;
    this.chunkGrid = chunkGrid;
    this.entities = entities;
    this.playerHeads = playerHeads;
    this.mode = mode;
    this.showPlayers = showPlayers;
    this.waypoints = waypoints == null ? null : new ArrayList<>(waypoints);
    this.filter = filter;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.waypointRows.clear();
    this.playerRows.clear();
    this.playersLabel = null;
    this.playerList = null;
    this.followToggle = null;
    this.caveToggle = null;
    this.gridToggle = null;
    this.entitiesToggle = null;
    this.playerHeadsToggle = null;
    this.search = null;

    VerticalListWidget<Widget> content = new VerticalListWidget<>();
    content.addId("atlas-content");
    content.addChild(ComponentWidget.i18n(I18N_PREFIX + "title").addId("atlas-title"));
    content.addChild(ComponentWidget.text(this.viewKey.context()).addId("atlas-subtitle"));
    this.addDimensions(content);
    this.addSubWorlds(content);
    this.addViewOptions(content);
    if (this.showPlayers) {
      this.addPlayers(content);
    }

    if (this.waypoints != null) {
      this.addWaypoints(content);
    }

    ScrollWidget scroll = new ScrollWidget(content);
    scroll.addId("atlas-scroll");
    this.addChild(scroll);
  }

  void update(boolean following, boolean caveLayer, boolean chunkGrid, boolean entities, boolean playerHeads) {
    this.following = following;
    this.caveLayer = caveLayer;
    this.chunkGrid = chunkGrid;
    this.entities = entities;
    this.playerHeads = playerHeads;
    if (this.gridToggle != null) {
      this.gridToggle.setValue(chunkGrid);
    }

    if (this.followToggle != null) {
      this.followToggle.setValue(following);
    }

    if (this.caveToggle != null) {
      this.caveToggle.setValue(caveLayer);
    }

    if (this.entitiesToggle != null) {
      this.entitiesToggle.setValue(entities);
    }

    if (this.playerHeadsToggle != null) {
      this.playerHeadsToggle.setValue(playerHeads);
    }
  }

  void updateDistances(double playerX, double playerZ) {
    for (WaypointRow row : this.waypointRows) {
      row.distance().setText(formatDistance(row.waypoint(), playerX, playerZ));
    }
  }

  /**
   * Rebuilds the player list when players join or leave, otherwise only updates the details.
   *
   * @param dimension the player's dimension, {@code null} if unknown
   */
  void updatePlayers(List<WorldMapPlayer> players, @Nullable String dimension, double x, double z) {
    boolean changed = !samePlayers(this.players, players);
    this.players = players;
    this.playerDimension = dimension;
    this.playerX = x;
    this.playerZ = z;
    if (this.playerList == null) {
      return;
    }

    if (changed) {
      this.fillPlayers(true);
      return;
    }

    for (PlayerRow row : this.playerRows) {
      for (WorldMapPlayer player : players) {
        if (player.uuid().equals(row.uuid())) {
          row.detail().setText(this.playerDetail(player));
          break;
        }
      }
    }
  }

  /**
   * @return whether the panel was built from these waypoints
   */
  boolean shows(@Nullable List<WorldMapWaypoint> waypoints) {
    return Objects.equals(this.waypoints, waypoints);
  }

  boolean isSearchFocused() {
    return this.search != null && this.search.isFocused();
  }

  void unfocusSearch() {
    if (this.search != null) {
      this.search.setFocused(false);
    }
  }

  private void addDimensions(VerticalListWidget<Widget> content) {
    content.addChild(label("dimension"));
    FlexibleContentWidget tabs = new FlexibleContentWidget();
    tabs.addId("atlas-tabs");
    for (MapWorldKey key : WorldMapNames.dimensionTabs(this.keys, this.viewKey, this.activeKey)) {
      DivWidget tab = new DivWidget();
      tab.addId("atlas-tab");
      if (key.dimension().equals(this.viewKey.dimension())) {
        tab.addId("atlas-tab-active");
      } else {
        this.makePressable(tab, () -> this.actions.showWorld(key));
      }

      String name = WorldMapNames.dimension(key.dimension());
      List<MapWorldKey> subWorlds = WorldMapNames.subWorlds(this.keys, key.dimension());
      tab.createContextMenuLazy(menu -> menu.addEntry(dangerEntry(
          Component.translatable(MENU_I18N_PREFIX + "clearMap"),
          () -> this.actions.clearWorlds(subWorlds, Component.text(name))
      )));
      tab.addChild(ComponentWidget.text(name));
      tabs.addFlexibleContent(tab);
    }

    content.addChild(tabs);
  }

  private void addSubWorlds(VerticalListWidget<Widget> content) {
    List<MapWorldKey> subWorlds = WorldMapNames.subWorlds(this.keys, this.viewKey.dimension());
    if (subWorlds.size() < 2) {
      return;
    }

    content.addChild(label("worlds"));
    for (MapWorldKey key : subWorlds) {
      String detail = key.equals(this.activeKey)
          ? I18n.getTranslation(I18N_PREFIX + "hereNow")
          : this.formatLastUsed(this.service.lastUsed(key));
      String name = WorldMapNames.subWorld(key, this.service.worldName(key));
      DivWidget row = row(
          ComponentWidget.text(name),
          detail == null ? null : ComponentWidget.text(detail)
      );
      if (key.equals(this.viewKey)) {
        row.addId("atlas-row-active");
      } else {
        this.makePressable(row, () -> this.actions.showWorld(key));
      }

      row.createContextMenuLazy(menu -> this.fillSubWorldMenu(menu, key, name, subWorlds));
      content.addChild(row);
    }
  }

  private void fillSubWorldMenu(ContextMenu menu, MapWorldKey key, String name, List<MapWorldKey> subWorlds) {
    menu.addEntry(menuEntry(
        Component.translatable(MENU_I18N_PREFIX + "rename"),
        () -> this.actions.renameWorld(key)
    ));
    menu.addEntry(ContextMenuEntry.builder()
        .text(Component.translatable(MENU_I18N_PREFIX + "mergeInto"))
        .subMenu(() -> {
          ContextMenu targets = new ContextMenu();
          for (MapWorldKey target : subWorlds) {
            if (!target.equals(key)) {
              String targetName = WorldMapNames.subWorld(target, this.service.worldName(target));
              targets.addEntry(menuEntry(
                  Component.text(targetName),
                  () -> this.actions.mergeWorlds(key, name, target, targetName)
              ));
            }
          }

          return targets;
        })
        .build());
    menu.addEntry(dangerEntry(
        Component.translatable(MENU_I18N_PREFIX + "clearMap"),
        () -> this.actions.clearWorlds(List.of(key), Component.text(name))
    ));
  }

  private void addViewOptions(VerticalListWidget<Widget> content) {
    content.addChild(label("view"));
    FlexibleContentWidget modes = new FlexibleContentWidget();
    modes.addId("atlas-tabs");
    for (MapMode mode : MapMode.values()) {
      DivWidget tab = new DivWidget();
      tab.addId("atlas-tab");
      if (mode == this.mode) {
        tab.addId("atlas-tab-active");
      } else {
        this.makePressable(tab, () -> this.actions.setMode(mode));
      }

      tab.addChild(ComponentWidget.i18n(I18N_PREFIX + "mode." + mode.name().toLowerCase(Locale.ROOT)));
      modes.addFlexibleContent(tab);
    }

    content.addChild(modes);
    if (this.current) {
      this.followToggle = this.addToggle(content, "follow", this.following, this.actions::setFollowing);
      this.caveToggle = this.addToggle(content, "caves", this.caveLayer, this.actions::setCaveLayer);
      this.entitiesToggle = this.addToggle(content, "entities", this.entities, this.actions::setEntities);
      this.playerHeadsToggle = this.addToggle(
          content, "playerHeads", this.playerHeads, this.actions::setPlayerHeads
      );
    }

    this.gridToggle = this.addToggle(content, "chunkGrid", this.chunkGrid, this.actions::setChunkGrid);

    DivWidget goTo = row(ComponentWidget.i18n(I18N_PREFIX + "goTo"), null);
    this.makePressable(goTo, this.actions::goToCoordinates);
    content.addChild(goTo);

    DivWidget export = row(ComponentWidget.i18n(I18N_PREFIX + "export"), null);
    this.makePressable(export, this.actions::exportImage);
    content.addChild(export);
  }

  private WorldMapToggleWidget addToggle(
      VerticalListWidget<Widget> content,
      String key,
      boolean value,
      Switchable switchable
  ) {
    WorldMapToggleWidget toggle = new WorldMapToggleWidget(value);

    DivWidget row = row(ComponentWidget.i18n(I18N_PREFIX + key), null);
    row.addChild(toggle);
    this.makePressable(row, () -> {
      boolean enabled = !toggle.value();
      toggle.setValue(enabled);
      switchable.switchValue(enabled);
    });
    content.addChild(row);
    return toggle;
  }

  private void addPlayers(VerticalListWidget<Widget> content) {
    ComponentWidget label = ComponentWidget.empty();
    label.addId("atlas-label");
    content.addChild(label);
    VerticalListWidget<Widget> list = new VerticalListWidget<>();
    list.addId("atlas-players");
    content.addChild(list);
    this.playersLabel = label;
    this.playerList = list;
    this.fillPlayers(false);
  }

  /**
   * Lists players in the player's own dimension first, nearest first, then the rest by name.
   *
   * @param initialized whether the list is already shown and needs its rows initialized
   */
  private void fillPlayers(boolean initialized) {
    this.playersLabel.setComponent(Component.translatable(
        I18N_PREFIX + "players",
        Component.text(String.valueOf(this.players.size()))
    ));
    this.playerRows.clear();
    if (initialized) {
      this.playerList.removeChildIf(widget -> true);
    }

    List<Widget> rows = new ArrayList<>();
    if (this.players.isEmpty()) {
      rows.add(ComponentWidget.i18n(I18N_PREFIX + "noPlayers").addId("atlas-empty"));
    }

    List<WorldMapPlayer> sorted = new ArrayList<>(this.players);
    sorted.sort(Comparator
        .comparingInt((WorldMapPlayer player) -> player.dimension().equals(this.playerDimension) ? 0 : 1)
        .thenComparingDouble(player -> player.dimension().equals(this.playerDimension)
            ? distanceSquared(player.x(), player.z(), this.playerX, this.playerZ)
            : 0.0D)
        .thenComparing(WorldMapPlayer::name, String.CASE_INSENSITIVE_ORDER));
    for (WorldMapPlayer player : sorted) {
      IconWidget icon = new IconWidget(Icon.head(player.uuid()));
      icon.addId("atlas-row-icon");
      ComponentWidget name = ComponentWidget.text(player.name());
      name.addId("atlas-row-name-indented");
      ComponentWidget detail = ComponentWidget.text(this.playerDetail(player));
      DivWidget row = row(name, detail);
      row.addChild(icon);
      UUID uuid = player.uuid();
      this.makePressable(row, () -> this.actions.focusPlayer(uuid));
      rows.add(row);
      this.playerRows.add(new PlayerRow(uuid, detail));
    }

    for (Widget row : rows) {
      if (initialized) {
        this.playerList.addChildInitialized(row);
      } else {
        this.playerList.addChild(row);
      }
    }
  }

  private String playerDetail(WorldMapPlayer player) {
    if (!player.dimension().equals(this.playerDimension)) {
      return WorldMapNames.dimension(player.dimension());
    }

    return Math.round(Math.sqrt(distanceSquared(player.x(), player.z(), this.playerX, this.playerZ))) + " m";
  }

  private static boolean samePlayers(List<WorldMapPlayer> first, List<WorldMapPlayer> second) {
    if (first.size() != second.size()) {
      return false;
    }

    for (WorldMapPlayer player : first) {
      boolean found = false;
      for (WorldMapPlayer other : second) {
        if (other.uuid().equals(player.uuid()) && other.dimension().equals(player.dimension())) {
          found = true;
          break;
        }
      }

      if (!found) {
        return false;
      }
    }

    return true;
  }

  private static double distanceSquared(double x, double z, double otherX, double otherZ) {
    double deltaX = x - otherX;
    double deltaZ = z - otherZ;
    return deltaX * deltaX + deltaZ * deltaZ;
  }

  private void addWaypoints(VerticalListWidget<Widget> content) {
    List<WorldMapWaypoint> waypoints = this.waypoints;
    content.addChild(ComponentWidget.component(Component.translatable(
        I18N_PREFIX + "waypoints",
        Component.text(String.valueOf(waypoints.size()))
    )).addId("atlas-label"));

    TextFieldWidget search = new TextFieldWidget();
    search.addId("atlas-search");
    search.placeholder(Component.translatable(I18N_PREFIX + "search"));
    search.setText(this.filter);
    search.updateListener(text -> {
      this.filter = text;
      this.actions.filterWaypoints(text);
      this.applyFilter();
    });
    content.addChild(search);
    this.search = search;

    if (waypoints.isEmpty()) {
      content.addChild(ComponentWidget.i18n(I18N_PREFIX + "noWaypoints").addId("atlas-empty"));
      return;
    }

    ClientPlayer player = this.current ? Laby.labyAPI().minecraft().getClientPlayer() : null;
    List<WorldMapWaypoint> sorted = new ArrayList<>(waypoints);
    if (player != null) {
      Position position = player.position();
      sorted.sort(Comparator.comparingDouble(
          waypoint -> distanceSquared(waypoint, position.getX(), position.getZ())
      ));
    }

    PlainTextComponentSerializer serializer = PlainTextComponentSerializer.plainText();
    for (WorldMapWaypoint waypoint : sorted) {
      IconWidget icon = new IconWidget(waypoint.icon());
      icon.addId("atlas-row-icon");
      icon.color().set(waypoint.iconColor());

      ComponentWidget name = ComponentWidget.component(waypoint.title());
      name.addId("atlas-row-name-indented");
      ComponentWidget distance = player == null
          ? ComponentWidget.empty()
          : ComponentWidget.text(formatDistance(waypoint, player.position().getX(), player.position().getZ()));

      DivWidget row = row(name, distance);
      row.addChild(icon);
      this.makePressable(row, () -> this.actions.focusWaypoint(waypoint));
      row.createContextMenuLazy(menu -> this.fillWaypointMenu(menu, waypoint));
      content.addChild(row);
      this.waypointRows.add(new WaypointRow(
          row,
          waypoint,
          serializer.serialize(waypoint.title()).toLowerCase(Locale.ROOT),
          distance
      ));
    }

    this.applyFilter();
  }

  private void fillWaypointMenu(ContextMenu menu, WorldMapWaypoint waypoint) {
    menu.addEntry(menuEntry(
        Component.translatable(MENU_I18N_PREFIX + "edit"),
        () -> this.actions.editWaypoint(waypoint)
    ));
    menu.addEntry(menuEntry(
        Component.translatable(MENU_I18N_PREFIX + "hide"),
        () -> this.actions.hideWaypoint(waypoint)
    ));
  }

  private void applyFilter() {
    String needle = this.filter.trim().toLowerCase(Locale.ROOT);
    for (WaypointRow row : this.waypointRows) {
      row.widget().setVisible(needle.isEmpty() || row.searchText().contains(needle));
    }
  }

  private void makePressable(DivWidget widget, Runnable action) {
    widget.setHoverCursor(CursorTypes.POINTING_HAND);
    widget.setPressable(action::run);
  }

  @Nullable
  private String formatLastUsed(long millis) {
    if (millis <= 0L) {
      return null;
    }

    long elapsed = Math.max(0L, System.currentTimeMillis() - millis);
    if (elapsed < MINUTE_MILLIS) {
      return I18n.getTranslation(I18N_PREFIX + "justNow");
    }

    if (elapsed < HOUR_MILLIS) {
      return I18n.getTranslation(I18N_PREFIX + "minutesAgo", elapsed / MINUTE_MILLIS);
    }

    if (elapsed < DAY_MILLIS) {
      return I18n.getTranslation(I18N_PREFIX + "hoursAgo", elapsed / HOUR_MILLIS);
    }

    return I18n.getTranslation(I18N_PREFIX + "daysAgo", elapsed / DAY_MILLIS);
  }

  private static ContextMenuEntry dangerEntry(Component text, Runnable action) {
    return menuEntry(text.color(NamedTextColor.RED), action);
  }

  private static ContextMenuEntry menuEntry(Component text, Runnable action) {
    return ContextMenuEntry.builder()
        .text(text)
        .clickHandler(entry -> {
          action.run();
          return true;
        })
        .build();
  }

  private static ComponentWidget label(String key) {
    return ComponentWidget.i18n(I18N_PREFIX + key).addId("atlas-label");
  }

  private static DivWidget row(ComponentWidget name, @Nullable ComponentWidget detail) {
    DivWidget row = new DivWidget();
    row.addId("atlas-row");
    name.addId("atlas-row-name");
    row.addChild(name);
    if (detail != null) {
      detail.addId("atlas-row-detail");
      row.addChild(detail);
    }

    return row;
  }

  private static String formatDistance(WorldMapWaypoint waypoint, double playerX, double playerZ) {
    return Math.round(Math.sqrt(distanceSquared(waypoint, playerX, playerZ))) + " m";
  }

  private static double distanceSquared(WorldMapWaypoint waypoint, double x, double z) {
    double deltaX = waypoint.x() - x;
    double deltaZ = waypoint.z() - z;
    return deltaX * deltaX + deltaZ * deltaZ;
  }

  interface Actions {

    void showWorld(MapWorldKey key);

    void setFollowing(boolean following);

    void setCaveLayer(boolean enabled);

    void setChunkGrid(boolean enabled);

    void setEntities(boolean enabled);

    void setPlayerHeads(boolean enabled);

    void setMode(MapMode mode);

    void exportImage();

    void goToCoordinates();

    void renameWorld(MapWorldKey key);

    void mergeWorlds(MapWorldKey source, String sourceName, MapWorldKey target, String targetName);

    /**
     * Asks before deleting the saved maps.
     *
     * @param name what the confirmation calls them
     */
    void clearWorlds(List<MapWorldKey> keys, Component name);

    void focusWaypoint(WorldMapWaypoint waypoint);

    void focusPlayer(UUID uuid);

    void editWaypoint(WorldMapWaypoint waypoint);

    void hideWaypoint(WorldMapWaypoint waypoint);

    void filterWaypoints(String filter);
  }

  private record PlayerRow(UUID uuid, ComponentWidget detail) {

  }

  private record WaypointRow(
      DivWidget widget,
      WorldMapWaypoint waypoint,
      String searchText,
      ComponentWidget distance
  ) {

  }
}
