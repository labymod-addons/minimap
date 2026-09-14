package net.labymod.addons.minimap.worldmap;

import java.util.List;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gfx.pipeline.renderer.text.TextRenderingOptions;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Round action menu of the world map. Pointing toward an entry selects it. Clicking the center
 * closes the menu without an action.
 */
final class WorldMapWheel {

  static final float RADIUS = 58.0F;
  static final float TITLE_SPACE = 16.0F;

  private static final float INNER_RADIUS = 20.0F;
  private static final float OUTER_RADIUS = 52.0F;
  private static final float HOVER_GROWTH = 3.0F;
  private static final float GAP_TURNS = 0.008F;
  private static final float SELECT_MIN_RADIUS = 12.0F;
  private static final float SELECT_MAX_RADIUS = 90.0F;
  private static final float ICON_SIZE = 10.0F;
  private static final float LABEL_SCALE = 0.6F;
  private static final float TITLE_SCALE = 0.7F;
  private static final float TITLE_PADDING = 3.0F;
  private static final float OPEN_START_SCALE = 0.7F;
  private static final long OPEN_NANOS = 120_000_000L;
  private static final double FULL_TURN = Math.PI * 2.0D;

  private static final int SEGMENT_COLOR = 0xC80C0F12;
  private static final int HOVER_COLOR = 0xF0ECF0EE;
  private static final int TITLE_BACKGROUND_COLOR = 0xC80C0F12;
  private static final int TEXT_COLOR = 0xFFFFFFFF;
  private static final int LABEL_COLOR = 0xC8FFFFFF;
  private static final int HOVER_TEXT_COLOR = 0xFF111614;

  private final float centerX;
  private final float centerY;
  private final Component title;
  private final List<Entry> entries;
  private final long openedAt = System.nanoTime();

  WorldMapWheel(float centerX, float centerY, Component title, List<Entry> entries) {
    this.centerX = centerX;
    this.centerY = centerY;
    this.title = title;
    this.entries = entries;
  }

  void render(ScreenCanvas canvas, float mouseX, float mouseY) {
    float progress = Math.min(1.0F, (System.nanoTime() - this.openedAt) / (float) OPEN_NANOS);
    float eased = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
    float scale = OPEN_START_SCALE + (1.0F - OPEN_START_SCALE) * eased;
    int alpha = (int) (eased * 255.0F);

    int hovered = this.entryAt(mouseX, mouseY);
    int count = this.entries.size();
    float turns = 1.0F / count;
    float innerRadius = INNER_RADIUS * scale;
    float labelRadius = (INNER_RADIUS + OUTER_RADIUS) / 2.0F * scale;
    for (int index = 0; index < count; index++) {
      Entry entry = this.entries.get(index);
      boolean selected = index == hovered;
      float middle = 0.5F + index * turns;
      float outerRadius = (OUTER_RADIUS + (selected ? HOVER_GROWTH : 0.0F)) * scale;
      this.arc(
          canvas,
          innerRadius, outerRadius,
          middle - turns / 2.0F + GAP_TURNS, middle + turns / 2.0F - GAP_TURNS,
          withAlpha(selected ? HOVER_COLOR : SEGMENT_COLOR, alpha)
      );

      float x = this.centerX + (float) Math.sin(middle * FULL_TURN) * labelRadius;
      float y = this.centerY + (float) Math.cos(middle * FULL_TURN) * labelRadius;
      canvas.submitIcon(
          entry.icon(),
          x - ICON_SIZE / 2.0F, y - ICON_SIZE / 2.0F - 3.0F,
          ICON_SIZE, ICON_SIZE,
          false,
          withAlpha(selected ? HOVER_TEXT_COLOR : TEXT_COLOR, alpha)
      );
      canvas.submitText(
          entry.label(),
          x, y + ICON_SIZE / 2.0F - 1.0F,
          withAlpha(selected ? HOVER_TEXT_COLOR : LABEL_COLOR, alpha),
          LABEL_SCALE,
          selected ? TextRenderingOptions.CENTERED : TextRenderingOptions.SHADOW | TextRenderingOptions.CENTERED
      );
    }

    float titleWidth = canvas.getTextWidth(this.title) * TITLE_SCALE + TITLE_PADDING * 2.0F;
    float titleHeight = canvas.getLineHeight() * TITLE_SCALE + TITLE_PADDING * 2.0F;
    float titleY = this.centerY + (OUTER_RADIUS + HOVER_GROWTH) * scale + 4.0F;
    canvas.submitRelativeRect(
        this.centerX - titleWidth / 2.0F, titleY,
        titleWidth, titleHeight,
        withAlpha(TITLE_BACKGROUND_COLOR, alpha)
    );
    canvas.submitComponent(
        this.title,
        this.centerX, titleY + TITLE_PADDING,
        withAlpha(TEXT_COLOR, alpha),
        TITLE_SCALE,
        TextRenderingOptions.SHADOW | TextRenderingOptions.CENTERED
    );
  }

  /**
   * @return the action of the entry under the cursor, {@code null} when pointing at the center or
   *     far outside
   */
  @Nullable
  Runnable actionAt(float mouseX, float mouseY) {
    int index = this.entryAt(mouseX, mouseY);
    return index == -1 ? null : this.entries.get(index).action();
  }

  private int entryAt(float mouseX, float mouseY) {
    float deltaX = mouseX - this.centerX;
    float deltaY = mouseY - this.centerY;
    float distanceSquared = deltaX * deltaX + deltaY * deltaY;
    if (distanceSquared < SELECT_MIN_RADIUS * SELECT_MIN_RADIUS
        || distanceSquared > SELECT_MAX_RADIUS * SELECT_MAX_RADIUS) {
      return -1;
    }

    // Turns counterclockwise from straight down, the same convention as the circle shader
    double angle = Math.atan2(deltaX, deltaY) / FULL_TURN;
    double shifted = angle - 0.5D + 0.5D / this.entries.size();
    double fraction = shifted - Math.floor(shifted);
    return MathHelper.clamp((int) (fraction * this.entries.size()), 0, this.entries.size() - 1);
  }

  /**
   * Draws a ring piece between two angles, in turns counterclockwise from straight down.
   */
  private void arc(ScreenCanvas canvas, float innerRadius, float outerRadius, float from, float to, int color) {
    canvas.submitCircle(
        this.centerX, this.centerY,
        innerRadius, outerRadius,
        (to - 0.5F) * 360.0F,
        (to - from) * 360.0F,
        color
    );
  }

  private static int withAlpha(int color, int alpha) {
    return ((color >>> 24) * alpha / 255) << 24 | (color & 0xFFFFFF);
  }

  record Entry(Icon icon, String label, Runnable action) {

  }
}
