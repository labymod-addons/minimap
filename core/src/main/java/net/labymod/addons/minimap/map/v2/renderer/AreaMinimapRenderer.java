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
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.world.MinecraftCamera;
import net.labymod.api.event.Subscribe;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;

/**
 * Draws the outlines of marked areas on the minimap, with the same rotation as the tile renderers.
 */
public class AreaMinimapRenderer {

  private static final float LINE_WIDTH = 1.0F;
  private static final int LINE_ALPHA = 0xD0;
  private static final float BLOCK_CIRCLE_MIN_PIXELS = 1.0F;

  private final MinimapConfigProvider configProvider;
  private final WorldMapService service;
  private final float[] corners = new float[8];
  private float playerX;
  private float playerZ;
  private float pixelLength;
  private float cos;
  private float sin;
  private float radius;

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
    this.cos = MathHelper.cos(MathHelper.toRadiansFloat(-yaw));
    this.sin = MathHelper.sin(MathHelper.toRadiansFloat(-yaw));

    // Blocks from the player to a corner of the zoomed map
    float visibleBlocks = this.radius / event.zoom() * 1.5F / this.pixelLength;
    ScreenCanvas canvas = event.context().canvas();
    boolean layered = false;
    for (MapArea area : this.service.areas(key).areas()) {
      if (!area.isVisible()
          || area.maxX() < this.playerX - visibleBlocks
          || area.minX() > this.playerX + visibleBlocks
          || area.maxZ() < this.playerZ - visibleBlocks
          || area.minZ() > this.playerZ + visibleBlocks) {
        continue;
      }

      if (!layered) {
        // Line shapes only report a tiny bounding box, so the canvas could sort them below the terrain
        canvas.nextLayer();
        layered = true;
      }

      int color = LINE_ALPHA << 24 | area.color();
      if (area.shape() == Shape.CIRCLE && this.pixelLength * event.zoom() >= BLOCK_CIRCLE_MIN_PIXELS) {
        this.renderBlockCircle(canvas, area, visibleBlocks, color);
        continue;
      }

      if (area.shape() == Shape.CIRCLE) {
        // A block is smaller than a pixel here, so the steps would not show
        float circleRadius = (area.maxX() - area.minX()) / 2.0F * this.pixelLength;
        canvas.submitCircle(
            this.screenX((float) area.centerX(), (float) area.centerZ()),
            this.screenY((float) area.centerX(), (float) area.centerZ()),
            Math.max(0.0F, circleRadius - LINE_WIDTH),
            circleRadius,
            color
        );
        continue;
      }

      this.corner(0, area.minX(), area.minZ());
      this.corner(2, area.maxX(), area.minZ());
      this.corner(4, area.maxX(), area.maxZ());
      this.corner(6, area.minX(), area.maxZ());
      for (int index = 0; index < 8; index += 2) {
        int next = (index + 2) % 8;
        line(
            canvas,
            this.corners[index], this.corners[index + 1],
            this.corners[next], this.corners[next + 1],
            color
        );
      }
    }
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

  private void worldLine(ScreenCanvas canvas, float fromX, float fromZ, float toX, float toZ, int color) {
    line(
        canvas,
        this.screenX(fromX, fromZ), this.screenY(fromX, fromZ),
        this.screenX(toX, toZ), this.screenY(toX, toZ),
        color
    );
  }

  private void corner(int index, float x, float z) {
    this.corners[index] = this.screenX(x, z);
    this.corners[index + 1] = this.screenY(x, z);
  }

  private float screenX(float x, float z) {
    float deltaX = (this.playerX - x) * this.pixelLength;
    float deltaZ = (this.playerZ - z) * this.pixelLength;
    return this.cos * deltaX - this.sin * deltaZ + this.radius;
  }

  private float screenY(float x, float z) {
    float deltaX = (this.playerX - x) * this.pixelLength;
    float deltaZ = (this.playerZ - z) * this.pixelLength;
    return this.sin * deltaX + this.cos * deltaZ + this.radius;
  }

  private static void line(ScreenCanvas canvas, float fromX, float fromY, float toX, float toY, int color) {
    float deltaX = toX - fromX;
    float deltaY = toY - fromY;
    float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    if (length < 0.01F) {
      return;
    }

    // Extending both ends by half the width closes the corners
    float alongX = deltaX / length * LINE_WIDTH / 2.0F;
    float alongY = deltaY / length * LINE_WIDTH / 2.0F;
    float normalX = -alongY;
    float normalY = alongX;
    canvas.submitTrapezoid(
        fromX - alongX + normalX, fromY - alongY + normalY,
        toX + alongX + normalX, toY + alongY + normalY,
        toX + alongX - normalX, toY + alongY - normalY,
        fromX - alongX - normalX, fromY - alongY - normalY,
        color
    );
  }
}
