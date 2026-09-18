package net.labymod.addons.minimap.worldmap;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.config.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.map.MinimapPlayerIcon;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.world.MapMode;
import net.labymod.addons.minimap.world.MapRegion;
import net.labymod.addons.minimap.world.MapRegionStore;
import net.labymod.addons.minimap.world.MapWorldKey;
import net.labymod.addons.minimap.world.WorldMapService;
import net.labymod.addons.minimap.world.WorldMapWaypoint;
import net.labymod.addons.minimap.world.WorldMapWaypoints;
import net.labymod.api.Laby;
import net.labymod.api.Textures.SpriteCommon;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.event.ClickEvent;
import net.labymod.api.client.component.format.TextDecoration;
import net.labymod.api.client.entity.Entity;
import net.labymod.api.client.entity.LivingEntity;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.gfx.pipeline.renderer.text.TextRenderingOptions;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.key.InputType;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.key.MouseButton;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.widget.attributes.bounds.BoundsType;
import net.labymod.api.client.gui.screen.widget.overlay.WidgetReference;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.client.gui.screen.widget.widgets.popup.SimpleAdvancedPopup;
import net.labymod.api.client.gui.screen.widget.widgets.popup.SimpleAdvancedPopup.SimplePopupButton;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.client.gui.window.Window;
import net.labymod.api.client.render.font.FontSize.PredefinedFontSize;
import net.labymod.api.client.world.MinecraftCamera;
import net.labymod.api.notification.Notification;
import net.labymod.api.util.I18n;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;
import org.jetbrains.annotations.Nullable;

/**
 * Full screen map of everything explored. Drag to pan, scroll to zoom towards the cursor, right
 * click for a wheel with waypoint, coordinate and clearing actions. Tab slides in the atlas panel.
 */
@Link("world-map.lss")
public class WorldMapActivity extends SimpleActivity implements WorldMapAtlasWidget.Actions {

  private static final String I18N_PREFIX = Util.NAMESPACE + ".worldMap.";
  private static final int BACKGROUND_COLOR = 0xFF0C0D10;
  private static final int TEXT_COLOR = 0xFFFFFFFF;
  private static final int CAVE_UNBUILT_DIM_COLOR = 0xB0000000;
  private static final int DRAG_LINE_COLOR = 0xE6FFFFFF;
  private static final int DRAG_LINE_OUTLINE_COLOR = 0x99000000;
  private static final float DRAG_LINE_WIDTH = 1.0F;
  private static final float DRAG_LINE_OUTLINE_WIDTH = 2.5F;
  private static final int CHUNK_LINE_ALPHA = 0x48;
  private static final int REGION_LINE_ALPHA = 0x80;
  private static final float CHUNK_GRID_MIN_SCALE = 0.5F;
  private static final float CHUNK_GRID_FADE_SCALE = 1.0F;
  private static final float PLAYER_ICON_SIZE = 8.0F;
  private static final float PLAYER_HEAD_SIZE = 8.0F;
  private static final float ENTITY_DOT_RADIUS = 1.5F;
  private static final int ENTITY_COLOR = 0xFFFFFFFF;
  private static final int ENTITY_OUTLINE_COLOR = 0xB0000000;
  private static final int SELECTION_FILL_COLOR = 0x40E04040;
  private static final int SELECTION_EDGE_COLOR = 0xFFE04040;
  private static final float WAYPOINT_ICON_SIZE = 12.0F;
  private static final float WAYPOINT_HIT_RADIUS = 7.0F;
  private static final float WAYPOINT_TITLE_SCALE = 0.75F;
  private static final float WAYPOINT_TITLE_MIN_MAP_SCALE = 1.0F;
  private static final float HOVER_DETAIL_MIN_MAP_SCALE = 0.75F;
  private static final float CHROME_MARGIN = 8.0F;
  private static final float SMALL_TEXT_SCALE = 0.75F;
  private static final float DIMENSION_SPACING = 14.0F;
  private static final float TOOLTIP_OFFSET = 10.0F;
  private static final float TOOLTIP_PADDING = 3.0F;
  private static final float CLICK_TOLERANCE = 3.0F;
  private static final long FLING_WINDOW_NANOS = 60_000_000L;
  private static final double FLING_SMOOTHING = 0.5D;
  private static final float ATLAS_ANIMATION_SECONDS = 0.18F;
  private static final int ATLAS_REFRESH_TICKS = 10;
  private static final int FALLBACK_WAYPOINT_Y = 64;

  private final MinimapConfigProvider configProvider;
  private final WorldMapService service;
  private final WorldMapOpener opener;
  private final MinimapConfiguration configuration;
  private final WorldMapRenderer renderer;
  private final CaveLayer caveLayer;
  private final WorldMapCamera camera = new WorldMapCamera();

  @Nullable
  private MapWorldKey viewKey;
  @Nullable
  private MapWorldKey initializedKey;
  private boolean followActive = true;
  private boolean cameraPlaced;
  private boolean dragging;
  private float dragDistance;
  private double pendingDeltaX;
  private double pendingDeltaY;
  private long lastDragNanos;
  private double flingX;
  private double flingZ;
  @Nullable
  private WorldMapWaypoint hoveredWaypoint;
  @Nullable
  private WorldMapWaypoint pressedWaypoint;
  @Nullable
  private WorldMapWaypoint draggedWaypoint;

  private List<MapWorldKey> dimensionTabs = List.of();
  private float[] dimensionTabX = new float[0];
  private float[] dimensionTabWidth = new float[0];
  private int subWorldIndex;
  private int subWorldCount;
  private boolean chromeClickable;
  private float dimensionBarLineHeight;
  private float followPillX;
  private float cavePillX;
  private float pillY;
  private float followPillWidth;
  private float cavePillWidth;
  private float pillHeight;

  @Nullable
  private WorldMapAtlasWidget atlas;
  @Nullable
  private DivWidget atlasHandle;
  @Nullable
  private IconWidget atlasHandleIcon;
  private float atlasProgress = -1.0F;
  private long lastFrameNanos;
  private String waypointFilter = "";
  private int ticks;
  private float textScale = SMALL_TEXT_SCALE;

  @Nullable
  private WorldMapWheel wheel;
  @Nullable
  private WidgetReference popup;
  private boolean selectingChunks;
  private boolean selectionDragging;
  private int selectionStartX;
  private int selectionStartZ;
  private int selectionEndX;
  private int selectionEndZ;

  public WorldMapActivity(
      MinimapConfigProvider configProvider,
      WorldMapService service,
      WorldMapOpener opener,
      MinimapConfiguration configuration,
      MinimapRenderer minimapRenderer,
      MinimapUniformBlocks uniformBlocks
  ) {
    this.configProvider = configProvider;
    this.service = service;
    this.opener = opener;
    this.configuration = configuration;
    this.renderer = new WorldMapRenderer(minimapRenderer, uniformBlocks);
    this.caveLayer = new CaveLayer(minimapRenderer, uniformBlocks);
    this.viewKey = service.activeKey();
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.initializedKey = this.viewKey;
    this.wheel = null;
    this.selectingChunks = false;
    this.selectionDragging = false;
    this.cancelWaypointDrag();
    this.atlas = null;
    this.atlasHandle = null;
    this.atlasHandleIcon = null;
    this.dimensionTabs = List.of();
    this.subWorldCount = 0;
    if (this.viewKey == null) {
      return;
    }

    List<MapWorldKey> keys = this.service.dimensions(this.viewKey);
    this.dimensionTabs = WorldMapNames.dimensionTabs(keys, this.viewKey, this.service.activeKey());
    this.dimensionTabX = new float[this.dimensionTabs.size()];
    this.dimensionTabWidth = new float[this.dimensionTabs.size()];
    List<MapWorldKey> subWorlds = WorldMapNames.subWorlds(keys, this.viewKey.dimension());
    this.subWorldCount = subWorlds.size();
    this.subWorldIndex = subWorlds.indexOf(this.viewKey) + 1;

    WorldMapWaypoints waypoints = this.service.waypoints();
    WorldMapAtlasWidget atlas = new WorldMapAtlasWidget(
        this,
        this.service,
        keys,
        this.viewKey,
        this.isViewingActive(),
        this.camera.isFollowing(),
        this.configuration.worldMapCaveLayer().get(),
        this.configuration.worldMapChunkGrid().get(),
        this.configuration.worldMapEntities().get(),
        this.configuration.worldMapMode().get(),
        waypoints == null ? null : waypoints.waypoints(this.viewKey),
        this.waypointFilter
    );
    atlas.addId("atlas");

    IconWidget handleIcon = new IconWidget(SpriteCommon.WHITE_GREATER_THAN);
    DivWidget handle = new DivWidget();
    handle.addId("atlas-handle");
    handle.addChild(handleIcon);
    handle.setPressable(this::toggleAtlas);

    Document document = this.document();
    document.addChild(atlas);
    document.addChild(handle);
    this.atlas = atlas;
    this.atlasHandle = handle;
    this.atlasHandleIcon = handleIcon;
  }

  @Override
  public boolean shouldRenderBackground() {
    return false;
  }

  @Override
  public void onOpenScreen() {
    super.onOpenScreen();
    this.service.setViewing(true);
  }

  @Override
  public void onCloseScreen() {
    super.onCloseScreen();
    this.service.setViewing(false);
    this.service.closeView();
    this.renderer.dispose();
    this.caveLayer.dispose();
  }

  @Override
  public void tick() {
    super.tick();
    this.ticks++;
    if (this.followActive) {
      this.viewKey = this.service.activeKey();
    }

    if (!Objects.equals(this.viewKey, this.initializedKey)) {
      // The cave layer tracks built chunks by position only and would keep the previous world
      this.caveLayer.dispose();
      this.reload();
      return;
    }

    Minecraft minecraft = Laby.labyAPI().minecraft();
    ClientPlayer player = minecraft.getClientPlayer();
    boolean current = this.isViewingActive();
    if (this.configuration.worldMapCaveLayer().get() && player != null && current) {
      this.caveLayer.tick(minecraft.clientWorld(), player, this.configuration.biomeBlend().get());
    }

    if (this.atlas == null) {
      return;
    }

    this.atlas.update(
        this.camera.isFollowing(),
        this.configuration.worldMapCaveLayer().get(),
        this.configuration.worldMapChunkGrid().get(),
        this.configuration.worldMapEntities().get()
    );
    if (this.ticks % ATLAS_REFRESH_TICKS != 0) {
      return;
    }

    WorldMapWaypoints waypoints = this.service.waypoints();
    if (!this.atlas.shows(waypoints == null ? null : waypoints.waypoints(this.viewKey))) {
      this.reload();
      return;
    }

    if (current && player != null) {
      Position position = player.position();
      this.atlas.updateDistances(position.getX(), position.getZ());
    }
  }

  @Override
  public void render(ScreenContext context) {
    Minecraft minecraft = Laby.labyAPI().minecraft();
    Window window = minecraft.minecraftWindow();
    float width = window.getScaledWidth();
    float height = window.getScaledHeight();
    ScreenCanvas canvas = context.canvas();
    canvas.submitRelativeRect(0.0F, 0.0F, width, height, BACKGROUND_COLOR);
    WorldMapTheme theme = WorldMapTheme.get();
    theme.refresh(window);
    this.textScale = theme.textScale(SMALL_TEXT_SCALE, PredefinedFontSize.MEDIUM);

    if (this.followActive) {
      this.viewKey = this.service.activeKey();
    }

    this.updateAtlasProgress();
    if (this.viewKey == null) {
      this.hoveredWaypoint = null;
      this.chromeClickable = false;
      canvas.submitText(
          I18n.getTranslation(I18N_PREFIX + "empty"),
          width / 2.0F, height / 2.0F,
          TEXT_COLOR,
          1.0F,
          TextRenderingOptions.SHADOW | TextRenderingOptions.CENTERED
      );
    } else {
      this.renderMap(context, minecraft, width, height);
    }

    super.render(context);
    if (this.wheel != null) {
      MutableMouse mouse = context.mouse();
      this.wheel.render(canvas, mouse.getX(), mouse.getY());
    }
  }

  @Override
  public boolean mouseClicked(MutableMouse mouse, MouseButton mouseButton) {
    if (this.wheel != null) {
      Runnable action = mouseButton.isLeft() ? this.wheel.actionAt(mouse.getX(), mouse.getY()) : null;
      this.wheel = null;
      if (action != null) {
        action.run();
      }

      return true;
    }

    if (this.draggedWaypoint != null) {
      this.cancelWaypointDrag();
      return true;
    }

    if (super.mouseClicked(mouse, mouseButton) || this.viewKey == null) {
      return true;
    }

    if (mouse.getX() < this.atlasRight()) {
      return true;
    }

    if (this.selectingChunks) {
      if (mouseButton.isLeft()) {
        this.selectionStartX = this.chunkAt(mouse.getX(), true);
        this.selectionStartZ = this.chunkAt(mouse.getY(), false);
        this.selectionEndX = this.selectionStartX;
        this.selectionEndZ = this.selectionStartZ;
        this.selectionDragging = true;
      } else {
        this.selectingChunks = false;
      }

      return true;
    }

    if (mouseButton.isLeft()) {
      if (this.clickChrome(mouse.getX(), mouse.getY())) {
        return true;
      }

      this.dragging = true;
      this.pressedWaypoint = this.hoveredWaypoint;
      this.dragDistance = 0.0F;
      this.pendingDeltaX = 0.0D;
      this.pendingDeltaY = 0.0D;
      this.flingX = 0.0D;
      this.flingZ = 0.0D;
      this.lastDragNanos = System.nanoTime();
      this.camera.stop();
      return true;
    }

    if (mouseButton.isRight()) {
      this.openWheel(mouse.getX(), mouse.getY());
      return true;
    }

    return false;
  }

  @Override
  public boolean mouseDragged(MutableMouse mouse, MouseButton button, double deltaX, double deltaY) {
    if (this.selectionDragging && button.isLeft()) {
      this.selectionEndX = this.chunkAt(mouse.getX(), true);
      this.selectionEndZ = this.chunkAt(mouse.getY(), false);
      return true;
    }

    if (!this.dragging || !button.isLeft()) {
      return super.mouseDragged(mouse, button, deltaX, deltaY);
    }

    long now = System.nanoTime();
    double seconds = Math.max((now - this.lastDragNanos) / 1.0E9D, 0.001D);
    this.lastDragNanos = now;
    this.dragDistance += (float) (Math.abs(deltaX) + Math.abs(deltaY));

    // Small movements while clicking must neither pan nor stop following the player
    if (this.dragDistance < CLICK_TOLERANCE) {
      this.pendingDeltaX += deltaX;
      this.pendingDeltaY += deltaY;
      return true;
    }

    if (this.pressedWaypoint != null && this.pressedWaypoint.movable()) {
      this.draggedWaypoint = this.pressedWaypoint;
      return true;
    }

    this.camera.pan(this.pendingDeltaX + deltaX, this.pendingDeltaY + deltaY);
    this.pendingDeltaX = 0.0D;
    this.pendingDeltaY = 0.0D;

    double scale = this.camera.scale();
    this.flingX = this.flingX * FLING_SMOOTHING - deltaX / scale / seconds * (1.0D - FLING_SMOOTHING);
    this.flingZ = this.flingZ * FLING_SMOOTHING - deltaY / scale / seconds * (1.0D - FLING_SMOOTHING);
    return true;
  }

  @Override
  public boolean mouseReleased(MutableMouse mouse, MouseButton mouseButton) {
    if (this.selectionDragging && mouseButton.isLeft()) {
      this.selectionDragging = false;
      this.selectingChunks = false;
      this.confirmClearChunks();
      return true;
    }

    if (!this.dragging || !mouseButton.isLeft()) {
      return super.mouseReleased(mouse, mouseButton);
    }

    this.dragging = false;
    WorldMapWaypoint pressed = this.pressedWaypoint;
    WorldMapWaypoint dragged = this.draggedWaypoint;
    this.pressedWaypoint = null;
    this.draggedWaypoint = null;
    if (dragged != null) {
      this.dropWaypoint(dragged, mouse.getX(), mouse.getY());
    } else if (this.dragDistance < CLICK_TOLERANCE) {
      WorldMapWaypoints waypoints = this.service.waypoints();
      if (waypoints != null && pressed != null) {
        waypoints.edit(pressed.id());
      }
    } else if (System.nanoTime() - this.lastDragNanos <= FLING_WINDOW_NANOS) {
      this.camera.fling(this.flingX, this.flingZ);
    }

    return true;
  }

  @Override
  public boolean mouseScrolled(MutableMouse mouse, double scrollDelta) {
    if (this.wheel == null
        && !this.selectionDragging
        && !super.mouseScrolled(mouse, scrollDelta)
        && mouse.getX() >= this.atlasRight()) {
      this.camera.zoom(mouse.getX(), mouse.getY(), scrollDelta);
    }

    return true;
  }

  @Override
  public boolean keyPressed(Key key, InputType type) {
    if (this.wheel != null && key == Key.ESCAPE) {
      this.wheel = null;
      return true;
    }

    if (this.draggedWaypoint != null && key == Key.ESCAPE) {
      this.cancelWaypointDrag();
      return true;
    }

    if (this.selectingChunks && key == Key.ESCAPE) {
      this.selectingChunks = false;
      this.selectionDragging = false;
      return true;
    }

    if (this.atlas != null && this.atlas.isSearchFocused()) {
      return super.keyPressed(key, type);
    }

    if (key == this.opener.openKey()) {
      this.opener.suppressOpen();
      //this.closeScreen();
      return true;
    }

    if (key == Key.TAB && this.atlas != null) {
      this.toggleAtlas();
      return true;
    }

    if (key == Key.G) {
      this.setChunkGrid(!this.configuration.worldMapChunkGrid().get());
      return true;
    }

    if (key == Key.SPACE && this.isViewingActive()) {
      this.camera.setFollowing(true);
      return true;
    }

    if (key == Key.C && this.isViewingActive()) {
      this.setCaveLayer(!this.configuration.worldMapCaveLayer().get());
      return true;
    }

    return super.keyPressed(key, type);
  }

  @Override
  public void showWorld(MapWorldKey key) {
    this.viewKey = key;
    this.followActive = key.equals(this.service.activeKey());
    this.camera.setFollowing(this.followActive);
    if (!this.followActive) {
      this.caveLayer.dispose();
    }

    this.reload();
  }

  @Override
  public void setFollowing(boolean following) {
    this.camera.setFollowing(following);
  }

  @Override
  public void setCaveLayer(boolean enabled) {
    if (this.configuration.worldMapCaveLayer().get() == enabled) {
      return;
    }

    this.configuration.worldMapCaveLayer().set(enabled);
    if (!enabled) {
      this.caveLayer.dispose();
    }
  }

  @Override
  public void setEntities(boolean enabled) {
    this.configuration.worldMapEntities().set(enabled);
  }

  @Override
  public void setMode(MapMode mode) {
    this.configuration.worldMapMode().set(mode);
    this.reload();
  }

  /**
   * Saves what the screen shows at its full resolution, without the chrome.
   */
  @Override
  public void exportImage() {
    if (this.viewKey == null) {
      return;
    }

    Window window = Laby.labyAPI().minecraft().minecraftWindow();
    float width = window.getScaledWidth();
    float height = window.getScaledHeight();
    float pixelScale = (float) window.getRawWidth() / width;
    this.service.exportImage(
        this.viewKey,
        this.configuration.worldMapMode().get(),
        this.configuration.biomeBlend().get(),
        this.camera.screenToWorldX(0.0F, width),
        this.camera.screenToWorldZ(0.0F, height),
        1.0D / (this.camera.scale() * pixelScale),
        window.getRawWidth(),
        window.getRawHeight(),
        this::exported
    );
  }

  @Override
  public void renameWorld(MapWorldKey key) {
    String name = this.service.worldName(key);
    this.popup = new WorldMapRenamePopup(name == null ? "" : name, newName -> {
      this.service.renameWorld(key, newName);
      this.reload();
    }).displayInOverlay();
  }

  @Override
  public void mergeWorlds(MapWorldKey source, String sourceName, MapWorldKey target, String targetName) {
    this.confirm(
        "merge",
        Component.translatable(
            I18N_PREFIX + "merge.description",
            Component.text(sourceName),
            Component.text(targetName)
        ),
        () -> this.service.mergeWorlds(
            source, target,
            () -> this.afterWorldsChanged(List.of(source), target)
        )
    );
  }

  @Override
  public void clearWorlds(List<MapWorldKey> keys, Component name) {
    this.confirm(
        "clearMap",
        Component.translatable(I18N_PREFIX + "clearMap.description", name),
        () -> this.service.deleteWorlds(keys, () -> this.afterWorldsChanged(keys, null))
    );
  }

  @Override
  public void focusWaypoint(WorldMapWaypoint waypoint) {
    this.camera.setFollowing(false);
    this.camera.reset(waypoint.x(), waypoint.z());
  }

  @Override
  public void editWaypoint(WorldMapWaypoint waypoint) {
    WorldMapWaypoints waypoints = this.service.waypoints();
    if (waypoints != null) {
      waypoints.edit(waypoint.id());
    }
  }

  @Override
  public void hideWaypoint(WorldMapWaypoint waypoint) {
    WorldMapWaypoints waypoints = this.service.waypoints();
    if (waypoints != null) {
      waypoints.hide(waypoint.id());
      this.reload();
    }
  }

  @Override
  public void filterWaypoints(String filter) {
    this.waypointFilter = filter;
  }

  @Override
  public void setChunkGrid(boolean enabled) {
    this.configuration.worldMapChunkGrid().set(enabled);
  }

  private void renderMap(ScreenContext context, Minecraft minecraft, float width, float height) {
    boolean current = this.isViewingActive();
    ClientPlayer player = minecraft.getClientPlayer();
    float partialTicks = minecraft.getPartialTicks();
    double playerX = 0.0D;
    double playerZ = 0.0D;
    if (player != null) {
      Position position = player.position();
      Position previous = player.previousPosition();
      playerX = position.lerpX(previous, partialTicks);
      playerZ = position.lerpZ(previous, partialTicks);
    }

    if (!this.cameraPlaced) {
      this.camera.reset(playerX, playerZ);
      this.cameraPlaced = true;
    }

    this.camera.update(width, height, current && player != null, playerX, playerZ);

    MapRegionStore store = this.service.openView(this.viewKey);
    Window window = minecraft.minecraftWindow();
    float pixelScale = (float) window.getRawWidth() / window.getScaledWidth();
    this.renderer.render(context, store, this.camera, width, height, pixelScale);
    this.renderer.renderOverlay(
        context, store, this.camera,
        width, height,
        pixelScale,
        this.configuration.worldMapMode().get(),
        this.configuration.biomeBlend().get()
    );
    if (current && this.configuration.worldMapCaveLayer().get()) {
      // Built cave chunks are opaque and cover the dimming, so only the area not built yet stays dark
      context.canvas().submitRelativeRect(0.0F, 0.0F, width, height, CAVE_UNBUILT_DIM_COLOR);
      this.caveLayer.render(context, this.camera, width, height);
    }

    if (this.configuration.worldMapChunkGrid().get() || this.selectingChunks) {
      this.renderChunkGrid(context.canvas(), width, height, pixelScale);
    }

    if (current && player != null && this.configuration.worldMapEntities().get()) {
      this.renderEntities(context.canvas(), minecraft, width, height, partialTicks);
    }

    MutableMouse mouse = context.mouse();
    if (this.selectingChunks) {
      this.renderSelection(context.canvas(), width, height, mouse.getX(), mouse.getY());
    }

    this.renderWaypoints(context, width, height, mouse.getX(), mouse.getY());
    if (current && player != null) {
      this.renderPlayers(context, minecraft, player, width, height, partialTicks, playerX, playerZ);
    }

    ScreenCanvas canvas = context.canvas();
    float atlasShown = this.atlasEase();
    int chromeAlpha = (int) ((1.0F - atlasShown) * 255.0F);
    this.chromeClickable = chromeAlpha > 128;
    if (chromeAlpha > 0) {
      this.renderDimensionBar(canvas, width, chromeAlpha);
      if (current) {
        this.renderStatePills(canvas, width, height, chromeAlpha);
        if (player != null) {
          this.renderPlayerCoordinates(canvas, player, width, chromeAlpha);
        }
      }
    }

    this.renderAtlasBackground(canvas, height);
    this.renderHints(canvas, this.atlasRight(), height, current);
    if ((!this.dragging || this.draggedWaypoint != null)
        && this.wheel == null
        && !this.isPopupOpen()
        && mouse.getX() >= this.atlasRight()) {
      this.renderTooltip(canvas, store, width, height, mouse.getX(), mouse.getY());
    }
  }

  private void renderAtlasBackground(ScreenCanvas canvas, float height) {
    if (this.atlasHandle == null) {
      return;
    }

    WorldMapTheme theme = WorldMapTheme.get();
    float atlasRight = this.atlasRight();
    if (atlasRight > 0.0F) {
      canvas.submitRelativeRect(0.0F, 0.0F, atlasRight, height, theme.panelColor());
      canvas.submitRelativeRect(atlasRight - 1.0F, 0.0F, 1.0F, height, theme.panelEdgeColor());
    }

    float handleHeight = this.atlasHandle.bounds().getHeight(BoundsType.OUTER);
    float handleWidth = this.atlasHandle.bounds().getWidth(BoundsType.OUTER);
    theme.handle(
        canvas,
        atlasRight, (height - handleHeight) / 2.0F,
        handleWidth, handleHeight,
        this.atlasHandle.isHovered()
    );
  }

  private void renderDimensionBar(ScreenCanvas canvas, float width, int alpha) {
    float totalWidth = DIMENSION_SPACING * (this.dimensionTabs.size() - 1);
    for (int index = 0; index < this.dimensionTabs.size(); index++) {
      this.dimensionTabWidth[index] = canvas.getTextWidth(
          WorldMapNames.dimension(this.dimensionTabs.get(index).dimension())
      );
      totalWidth += this.dimensionTabWidth[index];
    }

    float lineHeight = canvas.getLineHeight();
    this.dimensionBarLineHeight = lineHeight;
    float x = (width - totalWidth) / 2.0F;
    for (int index = 0; index < this.dimensionTabs.size(); index++) {
      MapWorldKey key = this.dimensionTabs.get(index);
      boolean shown = key.dimension().equals(this.viewKey.dimension());
      this.dimensionTabX[index] = x;
      canvas.submitText(
          WorldMapNames.dimension(key.dimension()),
          x, CHROME_MARGIN,
          withAlpha(TEXT_COLOR, shown ? alpha : alpha / 2),
          1.0F,
          TextRenderingOptions.SHADOW
      );
      if (shown) {
        canvas.submitRelativeRect(
            x, CHROME_MARGIN + lineHeight + 1.0F,
            this.dimensionTabWidth[index], 1.0F,
            withAlpha(TEXT_COLOR, alpha)
        );
      }

      x += this.dimensionTabWidth[index] + DIMENSION_SPACING;
    }

    if (this.subWorldCount > 1) {
      String name = this.service.worldName(this.viewKey);
      canvas.submitText(
          name == null
              ? I18n.getTranslation(I18N_PREFIX + "subWorldOf", this.subWorldIndex, this.subWorldCount)
              : I18n.getTranslation(I18N_PREFIX + "subWorldNamed", name, this.subWorldIndex, this.subWorldCount),
          width / 2.0F, CHROME_MARGIN + lineHeight + 5.0F,
          withAlpha(TEXT_COLOR, alpha * 3 / 5),
          this.textScale,
          TextRenderingOptions.SHADOW | TextRenderingOptions.CENTERED
      );
    }
  }

  private void renderStatePills(ScreenCanvas canvas, float width, float height, int alpha) {
    float lineHeight = canvas.getLineHeight() * this.textScale;
    this.pillHeight = lineHeight + 5.0F;
    this.pillY = height - CHROME_MARGIN - this.pillHeight;

    boolean following = this.camera.isFollowing();
    boolean caves = this.configuration.worldMapCaveLayer().get();
    String caveText = I18n.getTranslation(I18N_PREFIX + (caves ? "caveOn" : "caveOff"));
    this.cavePillWidth = canvas.getTextWidth(caveText) * this.textScale + 10.0F;
    this.cavePillX = width - CHROME_MARGIN - this.cavePillWidth;
    this.renderPill(canvas, caveText, this.cavePillX, this.cavePillWidth, caves, alpha);

    String followText = I18n.getTranslation(I18N_PREFIX + (following ? "following" : "freeCamera"));
    this.followPillWidth = canvas.getTextWidth(followText) * this.textScale + 10.0F;
    this.followPillX = this.cavePillX - 4.0F - this.followPillWidth;
    this.renderPill(canvas, followText, this.followPillX, this.followPillWidth, following, alpha);
  }

  private void renderPill(ScreenCanvas canvas, String text, float x, float width, boolean active, int alpha) {
    WorldMapTheme theme = WorldMapTheme.get();
    theme.pill(canvas, x, this.pillY, width, this.pillHeight, active, alpha);
    canvas.submitText(
        text,
        x + 5.0F, this.pillY + 3.0F,
        withAlpha(active ? theme.textColor() : theme.secondaryTextColor(), alpha),
        this.textScale,
        theme.textOptions()
    );
  }

  private void renderHints(ScreenCanvas canvas, float left, float height, boolean current) {
    float lineHeight = canvas.getLineHeight() * this.textScale;
    float y = height - CHROME_MARGIN - lineHeight - 5.0F;
    float x = left + CHROME_MARGIN;
    if (this.selectingChunks) {
      x = this.renderHint(canvas, I18n.getTranslation(I18N_PREFIX + "hint.drag"), "hint.selectChunks", x, y, lineHeight);
      this.renderHint(canvas, "Esc", "hint.cancel", x, y, lineHeight);
      return;
    }

    if (current) {
      x = this.renderHint(canvas, "Space", "hint.follow", x, y, lineHeight);
      x = this.renderHint(canvas, "C", "hint.caves", x, y, lineHeight);
    }

    x = this.renderHint(canvas, "G", "hint.grid", x, y, lineHeight);

    x = this.renderHint(canvas, "Tab", "hint.atlas", x, y, lineHeight);
    this.renderHint(canvas, I18n.getTranslation(I18N_PREFIX + "hint.rightClick"), "hint.actions", x, y, lineHeight);
  }

  private float renderHint(ScreenCanvas canvas, String key, String label, float x, float y, float lineHeight) {
    float keyWidth = canvas.getTextWidth(key) * this.textScale + 6.0F;
    String text = I18n.getTranslation(I18N_PREFIX + label);
    float textWidth = canvas.getTextWidth(text) * this.textScale;
    float hintHeight = lineHeight + 5.0F;
    WorldMapTheme theme = WorldMapTheme.get();
    theme.panel(canvas, x, y, keyWidth + textWidth + 7.0F, hintHeight, 255);
    theme.key(canvas, x, y, keyWidth, hintHeight, 255);
    canvas.submitText(key, x + 3.0F, y + 3.0F, theme.textColor(), this.textScale, theme.textOptions());

    float textX = x + keyWidth + 3.0F;
    canvas.submitText(text, textX, y + 3.0F, theme.secondaryTextColor(), this.textScale, theme.textOptions());
    return textX + textWidth + 8.0F;
  }

  private void renderTooltip(
      ScreenCanvas canvas,
      MapRegionStore store,
      float width, float height,
      float mouseX, float mouseY
  ) {
    int blockX = MathHelper.floor(this.camera.screenToWorldX(mouseX, width));
    int blockZ = MathHelper.floor(this.camera.screenToWorldZ(mouseY, height));
    Component text = this.describeLocation(store, blockX, blockZ);
    if (this.configuration.worldMapChunkGrid().get()) {
      text = text.append(Component.text("  ·  ")).append(Component.translatable(
          I18N_PREFIX + "chunk",
          Component.text(String.valueOf(blockX >> 4)),
          Component.text(String.valueOf(blockZ >> 4))
      ));
    }
    float tooltipWidth = canvas.getTextWidth(text) * this.textScale + TOOLTIP_PADDING * 2.0F;
    float tooltipHeight = canvas.getLineHeight() * this.textScale + TOOLTIP_PADDING * 2.0F;
    float x = mouseX + TOOLTIP_OFFSET;
    float y = mouseY + TOOLTIP_OFFSET;
    if (x + tooltipWidth > width) {
      x = mouseX - TOOLTIP_OFFSET - tooltipWidth;
    }

    if (y + tooltipHeight > height) {
      y = mouseY - TOOLTIP_OFFSET - tooltipHeight;
    }

    WorldMapTheme theme = WorldMapTheme.get();
    theme.panel(canvas, x, y, tooltipWidth, tooltipHeight, 255);
    canvas.submitComponent(
        text,
        x + TOOLTIP_PADDING, y + TOOLTIP_PADDING,
        theme.textColor(),
        this.textScale,
        theme.textOptions()
    );
  }

  private boolean clickChrome(float mouseX, float mouseY) {
    if (!this.chromeClickable) {
      return false;
    }

    if (this.dimensionTabs.size() > 1
        && mouseY >= CHROME_MARGIN - 2.0F
        && mouseY <= CHROME_MARGIN + this.dimensionBarLineHeight + 2.0F) {
      for (int index = 0; index < this.dimensionTabs.size(); index++) {
        MapWorldKey key = this.dimensionTabs.get(index);
        if (mouseX >= this.dimensionTabX[index]
            && mouseX <= this.dimensionTabX[index] + this.dimensionTabWidth[index]
            && !key.dimension().equals(this.viewKey.dimension())) {
          this.showWorld(key);
          return true;
        }
      }
    }

    if (!this.isViewingActive() || mouseY < this.pillY || mouseY > this.pillY + this.pillHeight) {
      return false;
    }

    if (mouseX >= this.followPillX && mouseX <= this.followPillX + this.followPillWidth) {
      this.camera.setFollowing(!this.camera.isFollowing());
      return true;
    }

    if (mouseX >= this.cavePillX && mouseX <= this.cavePillX + this.cavePillWidth) {
      this.setCaveLayer(!this.configuration.worldMapCaveLayer().get());
      return true;
    }

    return false;
  }

  private void renderWaypoints(
      ScreenContext context,
      float width, float height,
      float mouseX, float mouseY
  ) {
    this.hoveredWaypoint = null;
    WorldMapWaypoints waypoints = this.service.waypoints();
    if (waypoints == null || !this.configProvider.hudWidgetConfig().showWaypoints().get()) {
      return;
    }

    ScreenCanvas canvas = context.canvas();
    float titleScale = WorldMapTheme.get().textScale(WAYPOINT_TITLE_SCALE, PredefinedFontSize.MEDIUM);
    float left = -WAYPOINT_ICON_SIZE * 0.40625F;
    float top = -WAYPOINT_ICON_SIZE;
    for (WorldMapWaypoint waypoint : waypoints.waypoints(this.viewKey)) {
      float x = this.camera.worldToScreenX(waypoint.x(), width);
      float y = this.camera.worldToScreenY(waypoint.z(), height);
      boolean dragged = this.draggedWaypoint != null && waypoint.id().equals(this.draggedWaypoint.id());
      if (dragged) {
        float targetY = mouseY + WAYPOINT_ICON_SIZE / 2.0F;
        // Line shapes only report a tiny bounding box, so the canvas could sort them below the terrain
        canvas.nextLayer();
        thickLine(canvas, x, y, mouseX, targetY, DRAG_LINE_OUTLINE_WIDTH, DRAG_LINE_OUTLINE_COLOR);
        canvas.submitCircle(x, y, 3.0F, DRAG_LINE_OUTLINE_COLOR);
        thickLine(canvas, x, y, mouseX, targetY, DRAG_LINE_WIDTH, DRAG_LINE_COLOR);
        canvas.submitCircle(x, y, 2.0F, DRAG_LINE_COLOR);
        x = mouseX;
        y = targetY;
      } else if (!this.isOnScreen(x, y, width, height, WAYPOINT_ICON_SIZE)) {
        continue;
      }

      float deltaX = mouseX - x;
      float deltaY = mouseY - (y - WAYPOINT_ICON_SIZE / 2.0F);
      boolean hovered = dragged || (this.draggedWaypoint == null
          && !this.selectingChunks
          && !this.isPopupOpen()
          && deltaX * deltaX + deltaY * deltaY <= WAYPOINT_HIT_RADIUS * WAYPOINT_HIT_RADIUS);
      if (hovered) {
        this.hoveredWaypoint = waypoint;
      }

      context.pushStack();
      context.translate(x, y, 0.0F);

      context.pushStack();
      context.translate(Util.SHADOW_OFFSET, Util.SHADOW_OFFSET, 0.0F);
      canvas.submitIcon(
          waypoint.icon(),
          left, top, WAYPOINT_ICON_SIZE, WAYPOINT_ICON_SIZE,
          false,
          Util.applyShadowColor(waypoint.iconColor())
      );
      context.popStack();

      canvas.submitIcon(
          waypoint.icon(),
          left, top, WAYPOINT_ICON_SIZE, WAYPOINT_ICON_SIZE,
          false,
          waypoint.iconColor()
      );

      if (hovered || this.camera.scale() >= WAYPOINT_TITLE_MIN_MAP_SCALE) {
        canvas.submitComponent(
            waypoint.title(),
            0.0F, top - canvas.getLineHeight() * titleScale - 1.0F,
            TEXT_COLOR,
            titleScale,
            TextRenderingOptions.SHADOW | TextRenderingOptions.CENTERED
        );
      }

      context.popStack();
    }
  }

  private void renderPlayerCoordinates(ScreenCanvas canvas, ClientPlayer player, float width, int alpha) {
    Position position = player.position();
    String text = "X " + MathHelper.floor(position.getX())
        + "  Y " + MathHelper.floor(position.getY())
        + "  Z " + MathHelper.floor(position.getZ());
    float panelWidth = canvas.getTextWidth(text) * this.textScale + TOOLTIP_PADDING * 2.0F;
    float panelHeight = canvas.getLineHeight() * this.textScale + TOOLTIP_PADDING * 2.0F;
    float x = width - CHROME_MARGIN - panelWidth;
    WorldMapTheme theme = WorldMapTheme.get();
    theme.panel(canvas, x, CHROME_MARGIN, panelWidth, panelHeight, alpha);
    canvas.submitText(
        text,
        x + TOOLTIP_PADDING, CHROME_MARGIN + TOOLTIP_PADDING,
        withAlpha(theme.textColor(), alpha),
        this.textScale,
        theme.textOptions()
    );
  }

  private void renderEntities(
      ScreenCanvas canvas,
      Minecraft minecraft,
      float width, float height,
      float partialTicks
  ) {
    for (Entity entity : minecraft.clientWorld().getEntities()) {
      if (!(entity instanceof LivingEntity) || entity instanceof Player) {
        continue;
      }

      Position position = entity.position();
      Position previous = entity.previousPosition();
      float x = this.camera.worldToScreenX(position.lerpX(previous, partialTicks), width);
      float y = this.camera.worldToScreenY(position.lerpZ(previous, partialTicks), height);
      if (this.isOnScreen(x, y, width, height, ENTITY_DOT_RADIUS)) {
        canvas.submitCircle(x, y, ENTITY_DOT_RADIUS + 0.75F, ENTITY_OUTLINE_COLOR);
        canvas.submitCircle(x, y, ENTITY_DOT_RADIUS, ENTITY_COLOR);
      }
    }
  }

  private void renderPlayers(
      ScreenContext context,
      Minecraft minecraft,
      ClientPlayer self,
      float width, float height,
      float partialTicks,
      double selfX, double selfZ
  ) {
    MinimapHudWidgetConfig config = this.configProvider.hudWidgetConfig();
    ScreenCanvas canvas = context.canvas();
    float halfHead = PLAYER_HEAD_SIZE / 2.0F;
    if (config.showPlayers().get()) {
      for (Player player : minecraft.clientWorld().getPlayers()) {
        if (player == self) {
          continue;
        }

        Position position = player.position();
        Position previous = player.previousPosition();
        float x = this.camera.worldToScreenX(position.lerpX(previous, partialTicks), width);
        float y = this.camera.worldToScreenY(position.lerpZ(previous, partialTicks), height);
        if (this.isOnScreen(x, y, width, height, PLAYER_HEAD_SIZE)) {
          canvas.submitPlayerFace(
              player.profile(),
              x - halfHead, y - halfHead,
              PLAYER_HEAD_SIZE, PLAYER_HEAD_SIZE,
              -1,
              true
          );
        }
      }
    }

    if (!config.showOwnPlayer().get()) {
      return;
    }

    float x = this.camera.worldToScreenX(selfX, width);
    float y = this.camera.worldToScreenY(selfZ, height);
    MinimapPlayerIcon icon = config.playerIcon().get();
    if (icon == MinimapPlayerIcon.PLAYER_HEAD) {
      canvas.submitPlayerFace(
          self.profile(),
          x - halfHead, y - halfHead,
          PLAYER_HEAD_SIZE, PLAYER_HEAD_SIZE,
          -1,
          true
      );
      return;
    }

    MinecraftCamera minecraftCamera = minecraft.getCamera();
    float yaw = minecraftCamera == null ? 0.0F : minecraftCamera.getYaw();
    int color = config.playerColor().get().get();
    context.pushStack();
    context.translate(x, y, 0.0F);
    // A yaw of 0 faces south, which is down on the map
    context.stack().rotate(yaw + 180.0F, 0.0F, 0.0F, 1.0F);

    context.pushStack();
    context.translate(Util.SHADOW_OFFSET, Util.SHADOW_OFFSET, 0.0F);
    icon.render(context, PLAYER_ICON_SIZE, Util.applyShadowColor(color));
    context.popStack();

    icon.render(context, PLAYER_ICON_SIZE, color);
    context.popStack();
  }

  private Component describeLocation(MapRegionStore store, int blockX, int blockZ) {
    Component coordinates = Component.text("X " + blockX + "  Z " + blockZ);
    // Looking up columns zoomed out would load whole regions just for the hovered block
    if (this.camera.scale() < HOVER_DETAIL_MIN_MAP_SCALE) {
      return coordinates;
    }

    MapRegion region = store.getRegion(blockX >> MapRegion.BLOCK_SHIFT, blockZ >> MapRegion.BLOCK_SHIFT);
    int localX = blockX & (MapRegion.BLOCKS - 1);
    int localZ = blockZ & (MapRegion.BLOCKS - 1);
    if (region == null || !region.hasColumn(localX, localZ)) {
      return coordinates;
    }

    Component text = Component.text(
        "X " + blockX + "  Y " + region.height(localX, localZ) + "  Z " + blockZ
    );
    String biome = region.biome(localX, localZ);
    if (biome == null) {
      return text;
    }

    int separator = biome.indexOf(':');
    String translationKey = separator == -1
        ? "biome.minecraft." + biome
        : "biome." + biome.substring(0, separator) + "." + biome.substring(separator + 1);
    return text.append(Component.text("  ·  ")).append(Component.translatable(translationKey));
  }

  private void openWheel(float mouseX, float mouseY) {
    Window window = Laby.labyAPI().minecraft().minecraftWindow();
    float width = window.getScaledWidth();
    float height = window.getScaledHeight();
    int blockX = MathHelper.floor(this.camera.screenToWorldX(mouseX, width));
    int blockZ = MathHelper.floor(this.camera.screenToWorldZ(mouseY, height));
    int blockY = this.waypointY(blockX, blockZ);
    MapWorldKey key = this.viewKey;

    List<WorldMapWheel.Entry> entries = new ArrayList<>();
    WorldMapWaypoints waypoints = this.service.waypoints();
    WorldMapWaypoint waypoint = this.hoveredWaypoint;
    Component title;
    if (waypoints != null && waypoint != null) {
      title = waypoint.title();
      entries.add(wheelEntry(SpriteCommon.EDIT, "edit", () -> this.editWaypoint(waypoint)));
      entries.add(wheelEntry(SpriteCommon.X, "hide", () -> this.hideWaypoint(waypoint)));
    } else {
      title = Component.text("X " + blockX + "  Z " + blockZ);
      if (waypoints != null) {
        entries.add(wheelEntry(SpriteCommon.ADD, "create", () -> {
          waypoints.create(key, blockX, blockY, blockZ);
          this.reload();
        }));
      }

      int regionX = blockX >> MapRegion.BLOCK_SHIFT;
      int regionZ = blockZ >> MapRegion.BLOCK_SHIFT;
      MapRegionStore store = this.service.openView(key);
      if (store.isStored(regionX, regionZ)) {
        entries.add(wheelEntry(
            SpriteCommon.TRASH, "clearRegion",
            () -> this.confirmClearRegion(store, regionX, regionZ)
        ));
      }

      entries.add(wheelEntry(SpriteCommon.PAINT, "clearChunks", () -> this.selectingChunks = true));
    }

    entries.add(wheelEntry(
        SpriteCommon.COPY, "copy",
        () -> Laby.labyAPI().minecraft().setClipboard(blockX + " " + blockY + " " + blockZ)
    ));
    if (this.isViewingActive()) {
      entries.add(wheelEntry(SpriteCommon.MOVE, "follow", () -> this.camera.setFollowing(true)));
    }

    this.wheel = new WorldMapWheel(
        MathHelper.clamp(mouseX, WorldMapWheel.RADIUS, width - WorldMapWheel.RADIUS),
        MathHelper.clamp(mouseY, WorldMapWheel.RADIUS, height - WorldMapWheel.RADIUS - WorldMapWheel.TITLE_SPACE),
        title,
        entries
    );
  }

  private void confirmClearRegion(MapRegionStore store, int regionX, int regionZ) {
    this.confirm(
        "clearRegion",
        Component.translatable(
            I18N_PREFIX + "clearRegion.description",
            Component.text(String.valueOf(regionX)),
            Component.text(String.valueOf(regionZ))
        ),
        () -> store.clearChunks(regionX, regionZ, MapRegion.fullMask())
    );
  }

  private void confirmClearChunks() {
    int minX = Math.min(this.selectionStartX, this.selectionEndX);
    int minZ = Math.min(this.selectionStartZ, this.selectionEndZ);
    int maxX = Math.max(this.selectionStartX, this.selectionEndX);
    int maxZ = Math.max(this.selectionStartZ, this.selectionEndZ);
    MapRegionStore store = this.service.openView(this.viewKey);
    this.confirm(
        "clearChunks",
        Component.translatable(
            I18N_PREFIX + "clearChunks.description",
            Component.text(String.valueOf(maxX - minX + 1)),
            Component.text(String.valueOf(maxZ - minZ + 1))
        ),
        () -> clearChunks(store, minX, minZ, maxX, maxZ)
    );
  }

  private static void clearChunks(MapRegionStore store, int minX, int minZ, int maxX, int maxZ) {
    for (int regionX = minX >> MapRegion.CHUNK_SHIFT; regionX <= maxX >> MapRegion.CHUNK_SHIFT; regionX++) {
      for (int regionZ = minZ >> MapRegion.CHUNK_SHIFT; regionZ <= maxZ >> MapRegion.CHUNK_SHIFT; regionZ++) {
        if (!store.isStored(regionX, regionZ)) {
          continue;
        }

        int baseX = regionX << MapRegion.CHUNK_SHIFT;
        int baseZ = regionZ << MapRegion.CHUNK_SHIFT;
        long[] mask = new long[MapRegion.MASK_LONGS];
        for (int chunkX = Math.max(minX, baseX); chunkX <= Math.min(maxX, baseX + MapRegion.CHUNKS - 1); chunkX++) {
          for (int chunkZ = Math.max(minZ, baseZ); chunkZ <= Math.min(maxZ, baseZ + MapRegion.CHUNKS - 1); chunkZ++) {
            MapRegion.addToMask(mask, chunkX - baseX, chunkZ - baseZ);
          }
        }

        store.clearChunks(regionX, regionZ, mask);
      }
    }
  }

  /**
   * Switches to the replacement when the shown sub-world is gone. While following, the map shows
   * the recorded world again once the service resolves it.
   */
  private void afterWorldsChanged(List<MapWorldKey> removed, @Nullable MapWorldKey replacement) {
    if (!this.followActive && removed.contains(this.viewKey)) {
      if (replacement == null) {
        this.followActive = true;
        this.camera.setFollowing(true);
        this.viewKey = this.service.activeKey();
      } else {
        this.viewKey = replacement;
      }
    }

    this.reload();
  }

  private void exported(@Nullable Path file) {
    Component text = file == null
        ? Component.translatable(I18N_PREFIX + "export.failed")
        : Component.translatable(I18N_PREFIX + "export.saved", Component.text(file.getFileName().toString()));
    Laby.labyAPI().notificationController().push(Notification.builder()
        .title(Component.translatable(I18N_PREFIX + "export.title"))
        .text(text)
        .build());
    if (file != null) {
      Laby.labyAPI().minecraft().chatExecutor().displayClientMessage(Component.translatable(
          I18N_PREFIX + "export.saved",
          Component.text(file.getFileName().toString())
              .decorate(TextDecoration.UNDERLINED)
              .clickEvent(ClickEvent.openFile(file.toAbsolutePath().toString()))
      ));
    }
  }

  private void confirm(String key, Component description, Runnable action) {
    this.popup = SimpleAdvancedPopup.builder()
        .title(Component.translatable(I18N_PREFIX + key + ".title"))
        .description(description)
        .addButton(SimplePopupButton.cancel())
        .addButton(SimplePopupButton.create(
            "confirm",
            Component.translatable(I18N_PREFIX + key + ".confirm"),
            button -> action.run()
        ))
        .build()
        .displayInOverlay();
  }

  private boolean isPopupOpen() {
    return this.popup != null && this.popup.isAlive();
  }

  /**
   * @return the chunk under a screen coordinate on the x or z axis
   */
  private int chunkAt(float screen, boolean xAxis) {
    Window window = Laby.labyAPI().minecraft().minecraftWindow();
    double block = xAxis
        ? this.camera.screenToWorldX(screen, window.getScaledWidth())
        : this.camera.screenToWorldZ(screen, window.getScaledHeight());
    return MathHelper.floor(block) >> 4;
  }

  /**
   * Marks the dragged chunks, or the hovered chunk before the drag starts.
   */
  private void renderSelection(ScreenCanvas canvas, float width, float height, float mouseX, float mouseY) {
    int startX = this.selectionDragging ? this.selectionStartX : this.chunkAt(mouseX, true);
    int startZ = this.selectionDragging ? this.selectionStartZ : this.chunkAt(mouseY, false);
    int endX = this.selectionDragging ? this.selectionEndX : startX;
    int endZ = this.selectionDragging ? this.selectionEndZ : startZ;
    float left = this.camera.worldToScreenX((double) Math.min(startX, endX) * ChunkData.CHUNK_SIZE, width);
    float top = this.camera.worldToScreenY((double) Math.min(startZ, endZ) * ChunkData.CHUNK_SIZE, height);
    float right = this.camera.worldToScreenX((double) (Math.max(startX, endX) + 1) * ChunkData.CHUNK_SIZE, width);
    float bottom = this.camera.worldToScreenY((double) (Math.max(startZ, endZ) + 1) * ChunkData.CHUNK_SIZE, height);
    canvas.submitRelativeRect(left, top, right - left, bottom - top, SELECTION_FILL_COLOR);
    canvas.submitRelativeRect(left, top, right - left, 1.0F, SELECTION_EDGE_COLOR);
    canvas.submitRelativeRect(left, bottom - 1.0F, right - left, 1.0F, SELECTION_EDGE_COLOR);
    canvas.submitRelativeRect(left, top, 1.0F, bottom - top, SELECTION_EDGE_COLOR);
    canvas.submitRelativeRect(right - 1.0F, top, 1.0F, bottom - top, SELECTION_EDGE_COLOR);
  }

  private static WorldMapWheel.Entry wheelEntry(Icon icon, String key, Runnable action) {
    return new WorldMapWheel.Entry(icon, I18n.getTranslation(I18N_PREFIX + "wheel." + key), action);
  }

  private int waypointY(int blockX, int blockZ) {
    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    return this.surfaceY(
        blockX, blockZ,
        player == null ? FALLBACK_WAYPOINT_Y : MathHelper.floor(player.position().getY())
    );
  }

  /**
   * @return the block above the saved surface, or the fallback where nothing is saved
   */
  private int surfaceY(int blockX, int blockZ, int fallback) {
    MapRegion region = this.service.openView(this.viewKey).getRegion(
        blockX >> MapRegion.BLOCK_SHIFT,
        blockZ >> MapRegion.BLOCK_SHIFT
    );
    int localX = blockX & (MapRegion.BLOCKS - 1);
    int localZ = blockZ & (MapRegion.BLOCKS - 1);
    if (region != null && region.hasColumn(localX, localZ)) {
      return region.height(localX, localZ) + 1;
    }

    return fallback;
  }

  private void dropWaypoint(WorldMapWaypoint waypoint, float mouseX, float mouseY) {
    WorldMapWaypoints waypoints = this.service.waypoints();
    // Dropping onto the atlas panel puts the waypoint back
    if (waypoints == null || mouseX < this.atlasRight()) {
      return;
    }

    Window window = Laby.labyAPI().minecraft().minecraftWindow();
    int blockX = MathHelper.floor(this.camera.screenToWorldX(mouseX, window.getScaledWidth()));
    int blockZ = MathHelper.floor(this.camera.screenToWorldZ(mouseY, window.getScaledHeight()));
    waypoints.move(
        waypoint.id(),
        blockX + 0.5D,
        this.surfaceY(blockX, blockZ, MathHelper.floor(waypoint.y())),
        blockZ + 0.5D
    );
  }

  private void cancelWaypointDrag() {
    this.pressedWaypoint = null;
    this.draggedWaypoint = null;
    this.dragging = false;
  }

  /**
   * Chunk lines fade in while zooming in, region lines stay visible at every zoom.
   */
  private void renderChunkGrid(ScreenCanvas canvas, float width, float height, float pixelScale) {
    float fade = (this.camera.scale() - CHUNK_GRID_MIN_SCALE) / CHUNK_GRID_FADE_SCALE;
    int chunkAlpha = (int) (MathHelper.clamp(fade, 0.0F, 1.0F) * CHUNK_LINE_ALPHA);
    if (chunkAlpha > 0) {
      this.renderGridLines(canvas, width, height, ChunkData.CHUNK_SIZE, 1.0F / pixelScale, chunkAlpha);
    }

    this.renderGridLines(canvas, width, height, MapRegion.BLOCKS, 2.0F / pixelScale, REGION_LINE_ALPHA);
  }

  private void renderGridLines(
      ScreenCanvas canvas,
      float width, float height,
      int spacing,
      float thickness,
      int alpha
  ) {
    int color = alpha << 24 | 0xFFFFFF;
    long minX = Math.floorDiv(MathHelper.floor(this.camera.screenToWorldX(0.0F, width)), spacing) * (long) spacing;
    double maxX = this.camera.screenToWorldX(width, width);
    for (long blockX = minX; blockX <= maxX; blockX += spacing) {
      // Region lines are drawn in their own pass
      if (spacing != MapRegion.BLOCKS && blockX % MapRegion.BLOCKS == 0) {
        continue;
      }

      float x = this.camera.worldToScreenX(blockX, width);
      canvas.submitRelativeRect(x - thickness / 2.0F, 0.0F, thickness, height, color);
    }

    long minZ = Math.floorDiv(MathHelper.floor(this.camera.screenToWorldZ(0.0F, height)), spacing) * (long) spacing;
    double maxZ = this.camera.screenToWorldZ(height, height);
    for (long blockZ = minZ; blockZ <= maxZ; blockZ += spacing) {
      if (spacing != MapRegion.BLOCKS && blockZ % MapRegion.BLOCKS == 0) {
        continue;
      }

      float y = this.camera.worldToScreenY(blockZ, height);
      canvas.submitRelativeRect(0.0F, y - thickness / 2.0F, width, thickness, color);
    }
  }

  private void toggleAtlas() {
    boolean open = !this.configuration.worldMapAtlasOpen().get();
    this.configuration.worldMapAtlasOpen().set(open);
    if (!open && this.atlas != null) {
      this.atlas.unfocusSearch();
    }
  }

  private void updateAtlasProgress() {
    long now = System.nanoTime();
    float seconds = this.lastFrameNanos == 0L ? 0.0F : (now - this.lastFrameNanos) / 1.0E9F;
    this.lastFrameNanos = now;

    float target = this.configuration.worldMapAtlasOpen().get() ? 1.0F : 0.0F;
    if (this.atlasProgress < 0.0F) {
      this.atlasProgress = target;
    } else if (this.atlasProgress < target) {
      this.atlasProgress = Math.min(target, this.atlasProgress + seconds / ATLAS_ANIMATION_SECONDS);
    } else if (this.atlasProgress > target) {
      this.atlasProgress = Math.max(target, this.atlasProgress - seconds / ATLAS_ANIMATION_SECONDS);
    }

    if (this.atlas == null || this.atlasHandle == null) {
      return;
    }

    float offset = this.atlasRight() - this.atlas.bounds().getWidth(BoundsType.OUTER);
    this.atlas.setTranslateX(offset);
    this.atlasHandle.setTranslateX(offset);
    this.atlas.setVisible(this.atlasProgress > 0.0F);
    Icon handleIcon = this.configuration.worldMapAtlasOpen().get() ? SpriteCommon.WHITE_LESS_THAN : SpriteCommon.WHITE_GREATER_THAN;
    if (this.atlasHandleIcon.icon().get() != handleIcon) {
      this.atlasHandleIcon.icon().set(handleIcon);
    }
  }

  private float atlasEase() {
    float progress = Math.max(this.atlasProgress, 0.0F);
    return progress * progress * (3.0F - 2.0F * progress);
  }

  private float atlasRight() {
    return this.atlas == null ? 0.0F : this.atlasEase() * this.atlas.bounds().getWidth(BoundsType.OUTER);
  }

  private boolean isViewingActive() {
    return this.viewKey != null && this.viewKey.equals(this.service.activeKey());
  }

  private boolean isOnScreen(float x, float y, float width, float height, float margin) {
    return x >= -margin && x <= width + margin && y >= -margin && y <= height + margin;
  }

  /**
   * Draws a line as a filled quad of the given width.
   */
  private static void thickLine(
      ScreenCanvas canvas,
      float fromX, float fromY,
      float toX, float toY,
      float width,
      int color
  ) {
    float deltaX = toX - fromX;
    float deltaY = toY - fromY;
    float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    if (length < 1.0F) {
      return;
    }

    float normalX = -deltaY / length * width / 2.0F;
    float normalY = deltaX / length * width / 2.0F;
    canvas.submitTrapezoid(
        fromX + normalX, fromY + normalY,
        toX + normalX, toY + normalY,
        toX - normalX, toY - normalY,
        fromX - normalX, fromY - normalY,
        color
    );
  }

  private static int withAlpha(int color, int alpha) {
    int base = color >>> 24;
    return (base * alpha / 255) << 24 | (color & 0xFFFFFF);
  }
}
