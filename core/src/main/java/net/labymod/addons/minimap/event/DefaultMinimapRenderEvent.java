package net.labymod.addons.minimap.event;

import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.api.Laby;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.render.matrix.Stack;

public class DefaultMinimapRenderEvent implements MinimapRenderEvent {

  private Stack stack;
  private HudSize size;
  private MinimapBounds currentBounds;

  private Stage stage;

  public void fill(Stack stack, HudSize size, MinimapBounds currentBounds) {
    this.stack = stack;
    this.size = size;
    this.currentBounds = currentBounds;
  }

  public void fireWithStage(Stage stage) {
    this.stage = stage;

    Laby.fireEvent(this);
  }

  @Override
  public Stack stack() {
    return this.stack;
  }

  @Override
  public HudSize size() {
    return this.size;
  }

  @Override
  public MinimapBounds currentBounds() {
    return this.currentBounds;
  }

  @Override
  public Stage stage() {
    return this.stage;
  }
}
