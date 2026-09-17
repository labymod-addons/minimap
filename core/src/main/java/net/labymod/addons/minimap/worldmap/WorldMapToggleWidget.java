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

  private static final float KNOB_INSET = 1.5F;
  private static final float VANILLA_BORDER = 1.0F;

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
    WorldMapTheme theme = WorldMapTheme.get();
    int trackColor = theme.switchTrackColor(this.value);
    int knobColor = theme.switchKnobColor(this.value);
    if (theme.roundSwitch()) {
      canvas.submitCircle(x + radius, centerY, radius, trackColor);
      canvas.submitCircle(x + width - radius, centerY, radius, trackColor);
      canvas.submitRelativeRect(x + radius, y, width - height, height, trackColor);

      float knobX = this.value ? x + width - radius : x + radius;
      canvas.submitCircle(knobX, centerY, radius - KNOB_INSET, knobColor);
      return;
    }

    canvas.submitRelativeRect(x, y, width, height, theme.panelEdgeColor());
    canvas.submitRelativeRect(
        x + VANILLA_BORDER, y + VANILLA_BORDER,
        width - VANILLA_BORDER * 2.0F, height - VANILLA_BORDER * 2.0F,
        trackColor
    );
    float knobX = this.value ? x + width - height : x;
    canvas.submitRelativeRect(knobX, y, height, height, knobColor);
  }
}
