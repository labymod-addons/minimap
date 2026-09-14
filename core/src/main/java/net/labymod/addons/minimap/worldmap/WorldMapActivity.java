package net.labymod.addons.minimap.worldmap;

import java.util.List;
import java.util.Objects;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.config.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.map.MinimapPlayerIcon;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.world.MapRegion;
import net.labymod.addons.minimap.world.MapRegionStore;
import net.labymod.addons.minimap.world.MapWorldKey;
import net.labymod.addons.minimap.world.WorldMapService;
import net.labymod.addons.minimap.world.WorldMapWaypoint;
import net.labymod.addons.minimap.world.WorldMapWaypoints;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.gfx.pipeline.renderer.text.TextRenderingOptions;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.key.InputType;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.gui.screen.key.MouseButton;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.widget.context.ContextMenu;
import net.labymod.api.client.gui.screen.widget.context.ContextMenuEntry;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.window.Window;
import net.labymod.api.client.world.MinecraftCamera;
import net.labymod.api.util.I18n;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;
import org.jetbrains.annotations.Nullable;

/**
 * Full screen map of everything explored. Drag to pan, scroll to zoom towards the cursor, right
 * click for waypoint and coordinate actions.
 */
@Link("world-map.lss")
public class WorldMapActivity extends SimpleActivity {

  private static final String I18N_PREFIX = Util.NAMESPACE + ".worldMap.";
  private static final int BACKGROUND_COLOR = 0xFF0C0D10;
  private static final int TEXT_COLOR = 0xFFFFFFFF;
  private static final int CAVE_UNBUILT_DIM_COLOR = 0xB0000000;
  private static final float TEXT_PADDING = 6.0F;
  private static final float PLAYER_ICON_SIZE = 8.0F;
  private static final float PLAYER_HEAD_SIZE = 8.0F;
  private static final float WAYPOINT_ICON_SIZE = 12.0F;
  private static final float WAYPOINT_HIT_RADIUS = 7.0F;
  private static final float WAYPOINT_TITLE_SCALE = 0.75F;
  private static final float WAYPOINT_TITLE_MIN_MAP_SCALE = 1.0F;
  private static final float HOVER_DETAIL_MIN_MAP_SCALE = 0.75F;
  private static final float CLICK_TOLERANCE = 3.0F;
  private static final long FLING_WINDOW_NANOS = 60_000_000L;
  private static final double FLING_SMOOTHING = 0.5D;
  private static final int FALLBACK_WAYPOINT_Y = 64;

  private final MinimapConfigProvider configProvider;
  private final WorldMapService service;
  private final WorldMapOpener opener;
  private final WorldMapRenderer renderer;
  private final CaveLayer caveLayer;
  private final WorldMapCamera camera = new WorldMapCamera();

  @Nullable
  private MapWorldKey viewKey;
  @Nullable
  private MapWorldKey initializedKey;
  private boolean followActive = true;
  private boolean caveLayerEnabled;
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

  public WorldMapActivity(
      MinimapConfigProvider configProvider,
      WorldMapService service,
      WorldMapOpener opener,
      MinimapRenderer minimapRenderer,
      MinimapUniformBlocks uniformBlocks
  ) {
    this.configProvider = configProvider;
    this.service = service;
    this.opener = opener;
    this.renderer = new WorldMapRenderer(minimapRenderer, uniformBlocks);
    this.caveLayer = new CaveLayer(minimapRenderer, uniformBlocks);
    this.viewKey = service.activeKey();
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.initializedKey = this.viewKey;

    Document document = this.document();
    int slot = 0;
    if (this.isViewingActive()) {
      ButtonWidget followButton = ButtonWidget.i18n(
          I18N_PREFIX + "follow",
          () -> this.camera.setFollowing(true)
      );
      followButton.addId("slot-" + slot++);
      document.addChild(followButton);

      ButtonWidget caveButton = ButtonWidget.i18n(
          I18N_PREFIX + (this.caveLayerEnabled ? "caveOn" : "caveOff"),
          this::toggleCaveLayer
      );
      caveButton.addId("slot-" + slot++);
      document.addChild(caveButton);
    }

    if (this.viewKey != null && this.service.dimensions(this.viewKey).size() > 1) {
      ButtonWidget dimensionButton = ButtonWidget.component(
          Component.text(this.describe(this.viewKey)),
          this::showNextDimension
      );
      dimensionButton.addId("slot-" + slot);
      document.addChild(dimensionButton);
    }

    ButtonWidget closeButton = ButtonWidget.i18n(I18N_PREFIX + "close", this::closeScreen);
    closeButton.addId("close");
    document.addChild(closeButton);
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
    if (this.followActive) {
      this.viewKey = this.service.activeKey();
    }

    if (!Objects.equals(this.viewKey, this.initializedKey)) {
      // The cave layer tracks built chunks by position only and would keep the previous world
      this.caveLayer.dispose();
      this.reload();
    }

    Minecraft minecraft = Laby.labyAPI().minecraft();
    ClientPlayer player = minecraft.getClientPlayer();
    if (this.caveLayerEnabled && player != null && this.isViewingActive()) {
      this.caveLayer.tick(minecraft.clientWorld(), player);
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

    if (this.followActive) {
      this.viewKey = this.service.activeKey();
    }

    if (this.viewKey == null) {
      this.hoveredWaypoint = null;
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
  }

  @Override
  public boolean mouseClicked(MutableMouse mouse, MouseButton mouseButton) {
    if (super.mouseClicked(mouse, mouseButton) || this.viewKey == null) {
      return true;
    }

    if (mouseButton.isLeft()) {
      this.dragging = true;
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
      this.openContextMenu(mouse.getX(), mouse.getY());
      return true;
    }

    return false;
  }

  @Override
  public boolean mouseDragged(MutableMouse mouse, MouseButton button, double deltaX, double deltaY) {
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
    if (!this.dragging || !mouseButton.isLeft()) {
      return super.mouseReleased(mouse, mouseButton);
    }

    this.dragging = false;
    if (this.dragDistance < CLICK_TOLERANCE) {
      WorldMapWaypoints waypoints = this.service.waypoints();
      if (waypoints != null && this.hoveredWaypoint != null) {
        waypoints.edit(this.hoveredWaypoint.id());
      }
    } else if (System.nanoTime() - this.lastDragNanos <= FLING_WINDOW_NANOS) {
      this.camera.fling(this.flingX, this.flingZ);
    }

    return true;
  }

  @Override
  public boolean mouseScrolled(MutableMouse mouse, double scrollDelta) {
    if (!super.mouseScrolled(mouse, scrollDelta)) {
      this.camera.zoom(mouse.getX(), mouse.getY(), scrollDelta);
    }

    return true;
  }

  @Override
  public boolean keyPressed(Key key, InputType type) {
    if (key == this.opener.openKey()) {
      this.opener.suppressOpen();
      //this.closeScreen();
      return true;
    }

    if (key == Key.SPACE && this.isViewingActive()) {
      this.camera.setFollowing(true);
      return true;
    }

    return super.keyPressed(key, type);
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
    this.renderer.render(
        context, store, this.camera,
        width, height,
        (float) window.getRawWidth() / window.getScaledWidth()
    );
    if (current && this.caveLayerEnabled) {
      // Built cave chunks are opaque and cover the dimming, so only the area not built yet stays dark
      context.canvas().submitRelativeRect(0.0F, 0.0F, width, height, CAVE_UNBUILT_DIM_COLOR);
      this.caveLayer.render(context, this.camera, width, height);
    }

    MutableMouse mouse = context.mouse();
    this.renderWaypoints(context, width, height, mouse.getX(), mouse.getY());
    if (current && player != null) {
      this.renderPlayers(context, minecraft, player, width, height, partialTicks, playerX, playerZ);
    }

    ScreenCanvas canvas = context.canvas();
    canvas.submitText(
        this.describe(this.viewKey),
        TEXT_PADDING, TEXT_PADDING,
        TEXT_COLOR,
        1.0F,
        TextRenderingOptions.SHADOW
    );
    canvas.submitComponent(
        this.describeLocation(
            store,
            MathHelper.floor(this.camera.screenToWorldX(mouse.getX(), width)),
            MathHelper.floor(this.camera.screenToWorldZ(mouse.getY(), height))
        ),
        TEXT_PADDING, height - TEXT_PADDING - canvas.getLineHeight(),
        TEXT_COLOR,
        1.0F,
        TextRenderingOptions.SHADOW
    );
  }

  private void renderWaypoints(
      ScreenContext context,
      float width, float height,
      float mouseX, float mouseY
  ) {
    this.hoveredWaypoint = null;
    WorldMapWaypoints waypoints = this.service.waypoints();
    if (waypoints == null) {
      return;
    }

    ScreenCanvas canvas = context.canvas();
    float left = -WAYPOINT_ICON_SIZE * 0.40625F;
    float top = -WAYPOINT_ICON_SIZE;
    for (WorldMapWaypoint waypoint : waypoints.waypoints(this.viewKey)) {
      float x = this.camera.worldToScreenX(waypoint.x(), width);
      float y = this.camera.worldToScreenY(waypoint.z(), height);
      if (!this.isOnScreen(x, y, width, height, WAYPOINT_ICON_SIZE)) {
        continue;
      }

      float deltaX = mouseX - x;
      float deltaY = mouseY - (y - WAYPOINT_ICON_SIZE / 2.0F);
      boolean hovered = deltaX * deltaX + deltaY * deltaY <= WAYPOINT_HIT_RADIUS * WAYPOINT_HIT_RADIUS;
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
            0.0F, top - canvas.getLineHeight() * WAYPOINT_TITLE_SCALE - 1.0F,
            TEXT_COLOR,
            WAYPOINT_TITLE_SCALE,
            TextRenderingOptions.SHADOW | TextRenderingOptions.CENTERED
        );
      }

      context.popStack();
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
    Component coordinates = Component.text("X: " + blockX + "  Z: " + blockZ);
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
        "X: " + blockX + "  Y: " + region.height(localX, localZ) + "  Z: " + blockZ
    );
    String biome = region.biome(localX, localZ);
    if (biome == null) {
      return text;
    }

    int separator = biome.indexOf(':');
    String translationKey = separator == -1
        ? "biome.minecraft." + biome
        : "biome." + biome.substring(0, separator) + "." + biome.substring(separator + 1);
    return text.append(Component.text("  ")).append(Component.translatable(translationKey));
  }

  private String describe(MapWorldKey key) {
    String dimension = key.dimension();
    String path = dimension.substring(dimension.indexOf(':') + 1);
    StringBuilder name = new StringBuilder(path.length());
    boolean capitalize = true;
    for (int index = 0; index < path.length(); index++) {
      char character = path.charAt(index);
      if (character == '_' || character == '/') {
        name.append(' ');
        capitalize = true;
      } else {
        name.append(capitalize ? Character.toUpperCase(character) : character);
        capitalize = false;
      }
    }

    if (key.subWorld() == 0) {
      return name.toString();
    }

    return name + " - " + I18n.getTranslation(I18N_PREFIX + "subWorld", key.subWorld() + 1);
  }

  private void openContextMenu(float mouseX, float mouseY) {
    Window window = Laby.labyAPI().minecraft().minecraftWindow();
    int blockX = MathHelper.floor(this.camera.screenToWorldX(mouseX, window.getScaledWidth()));
    int blockZ = MathHelper.floor(this.camera.screenToWorldZ(mouseY, window.getScaledHeight()));
    int blockY = this.waypointY(blockX, blockZ);
    MapWorldKey key = this.viewKey;

    ContextMenu menu = new ContextMenu();
    WorldMapWaypoints waypoints = this.service.waypoints();
    WorldMapWaypoint waypoint = this.hoveredWaypoint;
    if (waypoints != null && waypoint != null) {
      menu.addEntry(entry("menu.editWaypoint", () -> waypoints.edit(waypoint.id())));
      menu.addEntry(entry("menu.hideWaypoint", () -> waypoints.hide(waypoint.id())));
    } else if (waypoints != null) {
      menu.addEntry(entry(
          "menu.createWaypoint",
          () -> waypoints.create(key, blockX, blockY, blockZ)
      ));
    }

    menu.addEntry(entry(
        "menu.copyCoordinates",
        () -> Laby.labyAPI().minecraft().setClipboard(blockX + " " + blockY + " " + blockZ)
    ));

    if (this.isViewingActive()) {
      menu.addEntry(entry("menu.centerPlayer", () -> this.camera.setFollowing(true)));
    }

    menu.open();
  }

  private int waypointY(int blockX, int blockZ) {
    MapRegion region = this.service.openView(this.viewKey).getRegion(
        blockX >> MapRegion.BLOCK_SHIFT,
        blockZ >> MapRegion.BLOCK_SHIFT
    );
    int localX = blockX & (MapRegion.BLOCKS - 1);
    int localZ = blockZ & (MapRegion.BLOCKS - 1);
    if (region != null && region.hasColumn(localX, localZ)) {
      return region.height(localX, localZ) + 1;
    }

    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    return player == null ? FALLBACK_WAYPOINT_Y : MathHelper.floor(player.position().getY());
  }

  private void toggleCaveLayer() {
    this.caveLayerEnabled = !this.caveLayerEnabled;
    if (!this.caveLayerEnabled) {
      this.caveLayer.dispose();
    }

    this.reload();
  }

  private void showNextDimension() {
    List<MapWorldKey> keys = this.service.dimensions(this.viewKey);
    this.viewKey = keys.get((keys.indexOf(this.viewKey) + 1) % keys.size());
    this.followActive = this.viewKey.equals(this.service.activeKey());
    this.camera.setFollowing(this.followActive);
    if (!this.followActive && this.caveLayerEnabled) {
      this.caveLayerEnabled = false;
      this.caveLayer.dispose();
    }

    this.reload();
  }

  private boolean isViewingActive() {
    return this.viewKey != null && this.viewKey.equals(this.service.activeKey());
  }

  private boolean isOnScreen(float x, float y, float width, float height, float margin) {
    return x >= -margin && x <= width + margin && y >= -margin && y <= height + margin;
  }

  private static ContextMenuEntry entry(String key, Runnable action) {
    return ContextMenuEntry.builder()
        .text(Component.translatable(I18N_PREFIX + key))
        .clickHandler(entry -> {
          action.run();
          return true;
        })
        .build();
  }
}
