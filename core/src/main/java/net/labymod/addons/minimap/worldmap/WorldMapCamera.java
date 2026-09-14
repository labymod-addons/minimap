package net.labymod.addons.minimap.worldmap;

/**
 * Center and zoom of the world map. Zoom eases towards its target while the point under the
 * cursor stays in place, and a released drag keeps its momentum.
 */
final class WorldMapCamera {

  static final float MIN_SCALE = 0.125F;
  static final float MAX_SCALE = 16.0F;
  private static final float DEFAULT_SCALE = 2.0F;
  private static final double ZOOM_STEP = 1.25D;
  private static final float ZOOM_SPEED = 14.0F;
  private static final float ZOOM_PRECISION = 0.001F;
  private static final double FRICTION = 6.0D;
  private static final double MIN_SCREEN_VELOCITY = 2.0D;
  private static final float MAX_FRAME_SECONDS = 0.1F;

  private double centerX;
  private double centerZ;
  private double velocityX;
  private double velocityZ;
  private float scale = DEFAULT_SCALE;
  private float targetScale = DEFAULT_SCALE;
  private float anchorX;
  private float anchorY;
  private boolean anchored;
  private boolean following = true;
  private long lastUpdateNanos;

  /**
   * @param canFollow whether the player is in the shown world
   */
  void update(float width, float height, boolean canFollow, double playerX, double playerZ) {
    long now = System.nanoTime();
    float seconds = this.lastUpdateNanos == 0L
        ? 0.0F
        : Math.min((now - this.lastUpdateNanos) / 1.0E9F, MAX_FRAME_SECONDS);
    this.lastUpdateNanos = now;

    boolean follow = this.following && canFollow;
    if (follow) {
      this.centerX = playerX;
      this.centerZ = playerZ;
      this.stop();
    } else if (this.velocityX != 0.0D || this.velocityZ != 0.0D) {
      this.centerX += this.velocityX * seconds;
      this.centerZ += this.velocityZ * seconds;
      double decay = Math.exp(-FRICTION * seconds);
      this.velocityX *= decay;
      this.velocityZ *= decay;
      if (Math.abs(this.velocityX * this.scale) < MIN_SCREEN_VELOCITY
          && Math.abs(this.velocityZ * this.scale) < MIN_SCREEN_VELOCITY) {
        this.stop();
      }
    }

    if (this.scale == this.targetScale) {
      return;
    }

    double anchorWorldX = this.screenToWorldX(this.anchorX, width);
    double anchorWorldZ = this.screenToWorldZ(this.anchorY, height);
    this.scale += (this.targetScale - this.scale) * (1.0F - (float) Math.exp(-ZOOM_SPEED * seconds));
    if (Math.abs(this.targetScale - this.scale) <= this.targetScale * ZOOM_PRECISION) {
      this.scale = this.targetScale;
    }

    if (this.anchored && !follow) {
      this.centerX = anchorWorldX - (this.anchorX - width / 2.0F) / this.scale;
      this.centerZ = anchorWorldZ - (this.anchorY - height / 2.0F) / this.scale;
    }
  }

  void reset(double x, double z) {
    this.centerX = x;
    this.centerZ = z;
    this.stop();
  }

  void pan(double screenDeltaX, double screenDeltaY) {
    this.following = false;
    this.centerX -= screenDeltaX / this.scale;
    this.centerZ -= screenDeltaY / this.scale;
  }

  /**
   * @param velocityX blocks per second
   * @param velocityZ blocks per second
   */
  void fling(double velocityX, double velocityZ) {
    this.velocityX = velocityX;
    this.velocityZ = velocityZ;
  }

  void stop() {
    this.velocityX = 0.0D;
    this.velocityZ = 0.0D;
  }

  void zoom(float screenX, float screenY, double steps) {
    double scale = this.targetScale * Math.pow(ZOOM_STEP, steps);
    this.targetScale = (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
    this.anchorX = screenX;
    this.anchorY = screenY;
    this.anchored = true;
  }

  void setFollowing(boolean following) {
    this.following = following;
  }

  boolean isFollowing() {
    return this.following;
  }

  /**
   * Screen pixels per block.
   */
  float scale() {
    return this.scale;
  }

  double screenToWorldX(float screenX, float width) {
    return this.centerX + (screenX - width / 2.0F) / this.scale;
  }

  double screenToWorldZ(float screenY, float height) {
    return this.centerZ + (screenY - height / 2.0F) / this.scale;
  }

  float worldToScreenX(double worldX, float width) {
    return (float) ((worldX - this.centerX) * this.scale + width / 2.0F);
  }

  float worldToScreenY(double worldZ, float height) {
    return (float) ((worldZ - this.centerZ) * this.scale + height / 2.0F);
  }
}
