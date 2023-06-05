package net.labymod.addons.minimap.api.event;

import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.event.Event;

public interface MinimapRenderEvent extends Event {

  Stack stack();

  HudSize size();

  MinimapBounds currentBounds();

  Stage stage();

  enum Stage {
    ROTATED,
    STRAIGHT
  }

}
