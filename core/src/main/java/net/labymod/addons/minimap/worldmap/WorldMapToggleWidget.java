package net.labymod.addons.minimap.worldmap;

import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.widget.SimpleWidget;
import net.labymod.api.client.gui.screen.widget.attributes.bounds.Bounds;
import net.labymod.api.client.gui.screen.widget.attributes.bounds.BoundsType;

/**
 * Pill shaped on/off indicator. The surrounding row handles clicks.
 */
final class WorldMapToggleWidget extends SimpleWidget {

  private static final int TRACK_ON_COLOR = 0xFF3E7C55;
  private static final int TRACK_OFF_COLOR = 0xFF2A3530;
  private static final int KNOB_ON_COLOR = 0xFFFFFFFF;
  private static final int KNOB_OFF_COLOR = 0xFF7D8983;
  private static final float KNOB_INSET = 1.5F;

  private boolean value;

  WorldMapToggleWidget(boolean value) {
    this.value = value;
  }

  boolean value() {
    return this.value;
  }

  void setValue(boolean value) {
    this.value = value;
  }

  @Override
  public void renderWidget(ScreenContext context) {
    super.renderWidget(context);
    Bounds bounds = this.bounds();
    float x = bounds.getX(BoundsType.INNER);
    float y = bounds.getY(BoundsType.INNER);
    float width = bounds.getWidth(BoundsType.INNER);
    float height = bounds.getHeight(BoundsType.INNER);
    float radius = height / 2.0F;
    float centerY = y + radius;

    ScreenCanvas canvas = context.canvas();
    int trackColor = this.value ? TRACK_ON_COLOR : TRACK_OFF_COLOR;
    canvas.submitCircle(x + radius, centerY, radius, trackColor);
    canvas.submitCircle(x + width - radius, centerY, radius, trackColor);
    canvas.submitRelativeRect(x + radius, y, width - height, height, trackColor);

    float knobX = this.value ? x + width - radius : x + radius;
    canvas.submitCircle(knobX, centerY, radius - KNOB_INSET, this.value ? KNOB_ON_COLOR : KNOB_OFF_COLOR);
  }
}
