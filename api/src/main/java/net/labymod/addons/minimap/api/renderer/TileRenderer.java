package net.labymod.addons.minimap.api.renderer;

import java.util.Collection;
import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;

public abstract class TileRenderer<T> {

  private final MinimapConfigProvider configProvider;

  private ClientPlayer clientPlayer;
  private float playerX;
  private float playerZ;

  private float scale;
  private float scaledRadius;

  private float radius;

  private float headRotationCos;
  private float headRotationSin;

  private float pixelLength;

  private float currentPixelDistanceX;
  private float currentPixelDistanceZ;

  protected TileRenderer(MinimapConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public final void renderTile(MinimapRenderEvent event) {
    if (!this.shouldRender(event.stage())) {
      return;
    }

    ClientPlayer clientPlayer = Laby.labyAPI().minecraft().getClientPlayer();
    if (clientPlayer == null) {
      return;
    }

    Collection<T> tiles = this.getTiles();
    if (tiles.isEmpty()) {
      return;
    }

    this.clientPlayer = clientPlayer;

    this.playerX = MathHelper.lerp(clientPlayer.getPosX(), clientPlayer.getPreviousPosX());
    this.playerZ = MathHelper.lerp(clientPlayer.getPosZ(), clientPlayer.getPreviousPosZ());

    this.scale = this.configProvider.widgetConfig().tileSize().get() / 10.0F;
    this.radius = event.size().getActualWidth() / 2.0F;

    this.scaledRadius = this.radius / event.zoom();

    float headRotationAngle = -clientPlayer.getRotationHeadYaw();

    this.headRotationCos = MathHelper.cos(MathHelper.toRadiansFloat(headRotationAngle));
    this.headRotationSin = MathHelper.sin(MathHelper.toRadiansFloat(headRotationAngle));

    this.pixelLength = event.pixelLength();

    this.renderTiles(event.stack(), tiles);
  }

  protected boolean shouldRenderTile(T t) {
    return true;
  }

  protected abstract void renderTile(Stack stack, T t);

  protected abstract float getTileX(T t);

  protected abstract float getTileZ(T t);

  protected abstract Collection<T> getTiles();

  @NotNull
  protected ClientPlayer clientPlayer() {
    return this.clientPlayer;
  }

  protected float getPlayerX() {
    return this.playerX;
  }

  protected float getPlayerZ() {
    return this.playerZ;
  }

  protected float getScale() {
    return this.scale;
  }

  protected float getScaledRadius() {
    return this.scaledRadius;
  }

  protected float getRadius() {
    return this.radius;
  }

  protected boolean shouldRender(MinimapRenderEvent.Stage stage) {
    return stage == Stage.STRAIGHT_ZOOMED_STENCIL;
  }

  protected float getCurrentPixelDistanceX() {
    return this.currentPixelDistanceX;
  }

  protected float getCurrentPixelDistanceZ() {
    return this.currentPixelDistanceZ;
  }

  protected float getCurrentDistance() {
    return (float) Math.sqrt(this.getCurrentPixelDistanceX() * this.getCurrentPixelDistanceX() + this.getCurrentPixelDistanceZ() * this.getCurrentPixelDistanceZ());
  }

  private void renderTiles(Stack stack, Collection<T> tiles) {
    for (T tile : tiles) {
      if (!this.shouldRenderTile(tile)) {
        continue;
      }

      this.currentPixelDistanceX = (this.playerX - this.getTileX(tile)) * this.pixelLength;
      this.currentPixelDistanceZ = (this.playerZ - this.getTileZ(tile)) * this.pixelLength;

      float rotX = this.headRotationCos * this.currentPixelDistanceX - this.headRotationSin * this.currentPixelDistanceZ;
      float rotZ = this.headRotationSin * this.currentPixelDistanceX + this.headRotationCos * this.currentPixelDistanceZ;

      float scaledRadius = this.getScaledRadius();
      if (rotX < -scaledRadius) {
        rotX = -scaledRadius;
      }

      if (rotZ < -scaledRadius) {
        rotZ = -scaledRadius;
      }

      if (rotX > scaledRadius) {
        rotX = scaledRadius;
      }

      if (rotZ > scaledRadius) {
        rotZ = scaledRadius;
      }

      stack.push();
      float radius = this.getRadius();
      stack.translate(rotX + radius, rotZ + radius, 0F);

      this.renderTile(stack, tile);

      stack.pop();
    }
  }

}
