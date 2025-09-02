package net.labymod.addons.minimap.api.event;

import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.map.MinimapCircle;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.event.Event;

public class MinimapRenderEvent implements Event {

  private ScreenContext context;
  private HudSize size;
  private MinimapBounds currentBounds;
  private MinimapCircle circle;
  private float pixelLength;
  private float zoom;

  private Stage stage;

  public void fill(ScreenContext context, HudSize size, MinimapBounds currentBounds, MinimapCircle circle) {
    this.context = context;
    this.size = size;
    this.currentBounds = currentBounds;
    this.circle = circle;
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

  public ScreenContext context() {
    return this.context;
  }

  public HudSize size() {
    return this.size;
  }

  public MinimapBounds currentBounds() {
    return this.currentBounds;
  }

  public MinimapCircle circle() {
    return this.circle;
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
    ROTATED_STENCIL,
    ROTATED,
    STRAIGHT_NORMAL_STENCIL,
    STRAIGHT_NORMAL,
    STRAIGHT_ZOOMED_STENCIL,
    STRAIGHT_ZOOMED
  }
}
