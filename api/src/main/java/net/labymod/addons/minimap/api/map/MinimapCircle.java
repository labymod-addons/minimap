package net.labymod.addons.minimap.api.map;

import net.labymod.api.client.gui.hud.position.HudSize;

public class MinimapCircle {

  private MinimapDisplayType displayType = MinimapDisplayType.ROUND;
  private HudSize size;
  private float distanceToCorner;

  private float circleX;
  private float circleY;

  public void init(MinimapDisplayType displayType, HudSize size, float distanceToCorner) {
    this.displayType = displayType;
    this.size = size;
    this.distanceToCorner = distanceToCorner;
  }

  public void calculate(double radians) {
    float radius = this.size.getActualWidth() / 2F;
    radius *= 0.95F;

    boolean circle = this.displayType.isCircle();

    float rotCenterX = this.size.getActualWidth() / 2F;
    float rotCenterY = this.size.getActualHeight() / 2F;

    float offsetCos = (float) Math.cos(-radians);
    float offsetSin = (float) Math.sin(-radians);

    offsetCos *= circle ? radius : this.distanceToCorner;
    offsetSin *= circle ? radius : this.distanceToCorner;

    if (offsetCos < -radius) {
      offsetCos = -radius;
    }
    if (offsetSin < -radius) {
      offsetSin = -radius;
    }

    if (offsetCos > radius) {
      offsetCos = radius;
    }
    if (offsetSin > radius) {
      offsetSin = radius;
    }

    this.circleX = rotCenterX + offsetCos;
    this.circleY = rotCenterY + offsetSin;
  }

  public float getCircleX() {
    return this.circleX;
  }

  public float getCircleY() {
    return this.circleY;
  }

  public float getDistanceToCorner() {
    return this.distanceToCorner;
  }
}
