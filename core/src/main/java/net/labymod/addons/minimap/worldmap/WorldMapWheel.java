package net.labymod.addons.minimap.worldmap;

import java.util.List;
import net.labymod.addons.minimap.world.MapArea.Shape;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gfx.pipeline.renderer.text.TextRenderingOptions;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.render.font.FontSize.PredefinedFontSize;
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
  private static final float GLYPH_SIZE = 8.0F;
  private static final float GLYPH_LINE_WIDTH = 1.0F;
  private static final float LABEL_SCALE = 0.6F;
  private static final float TITLE_SCALE = 0.7F;
  private static final float TITLE_PADDING = 3.0F;
  private static final float OPEN_START_SCALE = 0.7F;
  private static final long OPEN_NANOS = 120_000_000L;
  private static final double FULL_TURN = Math.PI * 2.0D;

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
    WorldMapTheme theme = WorldMapTheme.get();
    float labelScale = theme.textScale(LABEL_SCALE, PredefinedFontSize.SMALL);
    float titleScale = theme.textScale(TITLE_SCALE, PredefinedFontSize.MEDIUM);

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
          WorldMapTheme.withAlpha(selected ? theme.segmentHoverColor() : theme.panelColor(), alpha)
      );

      float x = this.centerX + (float) Math.sin(middle * FULL_TURN) * labelRadius;
      float y = this.centerY + (float) Math.cos(middle * FULL_TURN) * labelRadius;
      int iconColor = WorldMapTheme.withAlpha(selected ? theme.segmentHoverTextColor() : theme.textColor(), alpha);
      if (entry.shape() != null) {
        shapeGlyph(canvas, entry.shape(), x, y - 3.0F, iconColor);
      } else {
        canvas.submitIcon(
            entry.icon(),
            x - ICON_SIZE / 2.0F, y - ICON_SIZE / 2.0F - 3.0F,
            ICON_SIZE, ICON_SIZE,
            false,
            iconColor
        );
      }
      canvas.submitText(
          entry.label(),
          x, y + ICON_SIZE / 2.0F - 1.0F,
          WorldMapTheme.withAlpha(selected ? theme.segmentHoverTextColor() : theme.secondaryTextColor(), alpha),
          labelScale,
          selected ? TextRenderingOptions.CENTERED : theme.textOptions() | TextRenderingOptions.CENTERED
      );
    }

    float titleWidth = canvas.getTextWidth(this.title) * titleScale + TITLE_PADDING * 2.0F;
    float titleHeight = canvas.getLineHeight() * titleScale + TITLE_PADDING * 2.0F;
    float titleY = this.centerY + (OUTER_RADIUS + HOVER_GROWTH) * scale + 4.0F;
    theme.panel(canvas, this.centerX - titleWidth / 2.0F, titleY, titleWidth, titleHeight, alpha);
    canvas.submitComponent(
        this.title,
        this.centerX, titleY + TITLE_PADDING,
        WorldMapTheme.withAlpha(theme.textColor(), alpha),
        titleScale,
        theme.textOptions() | TextRenderingOptions.CENTERED
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

  private static void shapeGlyph(ScreenCanvas canvas, Shape shape, float centerX, float centerY, int color) {
    float half = GLYPH_SIZE / 2.0F;
    if (shape == Shape.CIRCLE) {
      canvas.submitCircle(centerX, centerY, half - GLYPH_LINE_WIDTH, half, color);
      return;
    }

    float left = centerX - half;
    float top = centerY - half;
    canvas.submitRelativeRect(left, top, GLYPH_SIZE, GLYPH_LINE_WIDTH, color);
    canvas.submitRelativeRect(left, top + GLYPH_SIZE - GLYPH_LINE_WIDTH, GLYPH_SIZE, GLYPH_LINE_WIDTH, color);
    canvas.submitRelativeRect(left, top + GLYPH_LINE_WIDTH, GLYPH_LINE_WIDTH, GLYPH_SIZE - GLYPH_LINE_WIDTH * 2.0F, color);
    canvas.submitRelativeRect(
        left + GLYPH_SIZE - GLYPH_LINE_WIDTH, top + GLYPH_LINE_WIDTH,
        GLYPH_LINE_WIDTH, GLYPH_SIZE - GLYPH_LINE_WIDTH * 2.0F,
        color
    );
  }

  /**
   * @param shape drawn instead of the icon when set
   */
  record Entry(@Nullable Icon icon, @Nullable Shape shape, String label, Runnable action) {

    Entry(Icon icon, String label, Runnable action) {
      this(icon, null, label, action);
    }
  }
}
