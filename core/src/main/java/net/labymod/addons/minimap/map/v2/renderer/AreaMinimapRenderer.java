package net.labymod.addons.minimap.map.v2.renderer;

import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.world.MapArea;
import net.labymod.addons.minimap.world.MapArea.Shape;
import net.labymod.addons.minimap.world.MapWorldKey;
import net.labymod.addons.minimap.world.WorldMapService;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.world.MinecraftCamera;
import net.labymod.api.event.Subscribe;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;

/**
 * Draws the outlines of marked areas on the minimap, with the same rotation as the tile renderers.
 * The outlines are axis aligned in world space, so they are drawn as rectangles in a rotated pose.
 * Unlike triangle shapes, rectangles respect the clip shape of the minimap.
 */
public class AreaMinimapRenderer {

  private static final float LINE_WIDTH = 1.0F;
  private static final int LINE_ALPHA = 0xD0;
  private static final float BLOCK_CIRCLE_MIN_PIXELS = 1.0F;

  private final MinimapConfigProvider configProvider;
  private final WorldMapService service;
  private float playerX;
  private float playerZ;
  private float pixelLength;
  private float radius;
  private float lineWidth;

  public AreaMinimapRenderer(MinimapConfigProvider configProvider, WorldMapService service) {
    this.configProvider = configProvider;
    this.service = service;
  }

  @Subscribe
  public void onRender(MinimapRenderEvent event) {
    if (event.stage() != Stage.STRAIGHT_ZOOMED_STENCIL
        || !this.configProvider.hudWidgetConfig().showAreas().get()) {
      return;
    }

    MapWorldKey key = this.service.activeKey();
    Minecraft minecraft = Laby.labyAPI().minecraft();
    ClientPlayer player = minecraft.getClientPlayer();
    if (key == null || player == null) {
      return;
    }

    float partialTicks = minecraft.getPartialTicks();
    Position position = player.position();
    Position previous = player.previousPosition();
    this.playerX = (float) position.lerpX(previous, partialTicks);
    this.playerZ = (float) position.lerpZ(previous, partialTicks);
    this.pixelLength = event.pixelLength();
    this.radius = event.size().getActualWidth() / 2.0F;

    MinecraftCamera camera = minecraft.getCamera();
    float yaw = camera == null ? 0.0F : camera.getYaw();
    this.lineWidth = LINE_WIDTH / this.pixelLength;

    // Blocks from the player to a corner of the zoomed map
    float visibleBlocks = this.radius / event.zoom() * 1.5F / this.pixelLength;
    ScreenContext context = event.context();
    ScreenCanvas canvas = context.canvas();
    context.pushStack();
    // Maps block offsets from the player onto the minimap, rotated like the tile renderers
    context.translate(this.radius, this.radius, 0.0F);
    context.stack().rotate(-yaw, 0.0F, 0.0F, 1.0F);
    context.scale(-this.pixelLength, -this.pixelLength, 1.0F);
    for (MapArea area : this.service.areas(key).areas()) {
      if (!area.isVisible()
          || area.maxX() < this.playerX - visibleBlocks
          || area.minX() > this.playerX + visibleBlocks
          || area.maxZ() < this.playerZ - visibleBlocks
          || area.minZ() > this.playerZ + visibleBlocks) {
        continue;
      }

      int color = LINE_ALPHA << 24 | area.color();
      if (area.shape() == Shape.CIRCLE && this.pixelLength * event.zoom() >= BLOCK_CIRCLE_MIN_PIXELS) {
        this.renderBlockCircle(canvas, area, visibleBlocks, color);
        continue;
      }

      if (area.shape() == Shape.CIRCLE) {
        // A block is smaller than a pixel here, so the steps would not show
        float circleRadius = (area.maxX() - area.minX()) / 2.0F;
        canvas.submitCircle(
            (float) area.centerX() - this.playerX,
            (float) area.centerZ() - this.playerZ,
            Math.max(0.0F, circleRadius - this.lineWidth),
            circleRadius,
            color
        );
        continue;
      }

      this.worldLine(canvas, area.minX(), area.minZ(), area.maxX(), area.minZ(), color);
      this.worldLine(canvas, area.maxX(), area.minZ(), area.maxX(), area.maxZ(), color);
      this.worldLine(canvas, area.maxX(), area.maxZ(), area.minX(), area.maxZ(), color);
      this.worldLine(canvas, area.minX(), area.maxZ(), area.minX(), area.minZ(), color);
    }

    context.popStack();
  }

  /**
   * Draws one pair of side lines per run of equal-width rows, plus the steps between runs.
   */
  private void renderBlockCircle(ScreenCanvas canvas, MapArea area, float visibleBlocks, int color) {
    int firstZ = Math.max(area.minZ(), MathHelper.floor(this.playerZ - visibleBlocks));
    int endZ = Math.min(area.maxZ(), MathHelper.ceil(this.playerZ + visibleBlocks));
    if (firstZ >= endZ) {
      return;
    }

    float middle = (float) area.centerX();
    int previousInset = area.circleInset(firstZ - 1);
    int runStart = firstZ;
    int runInset = area.circleInset(firstZ);
    for (int z = firstZ + 1; z <= endZ; z++) {
      int inset = area.circleInset(z);
      if (z < endZ && inset == runInset) {
        continue;
      }

      float left = area.minX() + runInset;
      float right = area.maxX() - runInset;
      this.worldLine(canvas, left, runStart, left, z, color);
      this.worldLine(canvas, right, runStart, right, z, color);
      this.renderStep(canvas, area, middle, runStart, previousInset, runInset, color);

      previousInset = runInset;
      runStart = z;
      runInset = inset;
    }

    this.renderStep(canvas, area, middle, endZ, previousInset, runInset, color);
  }

  /**
   * Draws the horizontal outline where two rows differ in width.
   */
  private void renderStep(
      ScreenCanvas canvas,
      MapArea area,
      float middle,
      float z,
      int aboveInset, int belowInset,
      int color
  ) {
    if (aboveInset == belowInset) {
      return;
    }

    int outer = Math.min(aboveInset, belowInset);
    int inner = Math.max(aboveInset, belowInset);
    this.worldLine(canvas, area.minX() + outer, z, Math.min(area.minX() + inner, middle), z, color);
    this.worldLine(canvas, Math.max(area.maxX() - inner, middle), z, area.maxX() - outer, z, color);
  }

  /**
   * Draws an axis aligned line between two block positions, extended by half the width at both
   * ends to close the corners.
   */
  private void worldLine(ScreenCanvas canvas, float fromX, float fromZ, float toX, float toZ, int color) {
    float halfWidth = this.lineWidth / 2.0F;
    canvas.submitRelativeRect(
        Math.min(fromX, toX) - this.playerX - halfWidth,
        Math.min(fromZ, toZ) - this.playerZ - halfWidth,
        Math.abs(toX - fromX) + this.lineWidth,
        Math.abs(toZ - fromZ) + this.lineWidth,
        color
    );
  }
}
