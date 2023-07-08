package net.labymod.addons.minimap.api.event;

import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.event.Event;

public class MinimapRenderEvent implements Event {

  private Stack stack;
  private HudSize size;
  private MinimapBounds currentBounds;
  private float pixelLength;
  private float zoom;

  private Stage stage;

  public void fill(Stack stack, HudSize size, MinimapBounds currentBounds) {
    this.stack = stack;
    this.size = size;
    this.currentBounds = currentBounds;
  }

  public void setPixelLength(float pixelLength) {
    this.pixelLength = pixelLength;
  }

  public void setZoom(float zoom) {
    this.zoom = zoom;
  }

  public void fireWithStage(Stage stage) {
    this.stage = stage;

    Laby.fireEvent(this);
  }

  public Stack stack() {
    return this.stack;
  }

  public HudSize size() {
    return this.size;
  }

  public MinimapBounds currentBounds() {
    return this.currentBounds;
  }

  public Stage stage() {
    return this.stage;
  }

  public float pixelLength() {
    return this.pixelLength;
  }

  public float zoom() {
    return this.zoom;
  }

  public enum Stage {
    ROTATED,
    STRAIGHT_NORMAL,
    STRAIGHT_ZOOMED
  }
}
