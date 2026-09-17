package net.labymod.addons.minimap.worldmap;

import net.labymod.api.Laby;
import net.labymod.api.client.gfx.pipeline.renderer.text.TextRenderingOptions;
import net.labymod.api.client.gui.lss.variable.LssVariable;
import net.labymod.api.client.gui.screen.state.RoundedData;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.window.Window;
import net.labymod.api.client.render.font.FontSize.PredefinedFontSize;
import org.jetbrains.annotations.Nullable;

/**
 * Colors and shapes of the canvas drawn world map chrome. Mirrors the fancy theme with its color
 * variables and every other theme with the vanilla look.
 */
final class WorldMapTheme {

  private static final String FANCY_THEME_ID = "fancy";
  private static final float FANCY_RADIUS = 4.0F;
  private static final RoundedData ROUNDED = RoundedData.builder().setRadius(FANCY_RADIUS).build();
  private static final RoundedData RIGHT_ROUNDED = RoundedData.builder()
      .setRightTopRadius(FANCY_RADIUS)
      .setRightBottomRadius(FANCY_RADIUS)
      .build();

  private static final int FANCY_TEXT_COLOR = 0xFFF1F1F1;
  private static final int FANCY_SECONDARY_TEXT_COLOR = 0xFFA0A0A0;
  private static final int FANCY_SWITCH_ON_COLOR = 0xFF2196F3;
  private static final int FANCY_SELECTION_COLOR = 0xCC0078D7;

  private static final int VANILLA_PANEL_COLOR = 0xC8000000;
  private static final int VANILLA_PANEL_HOVER_COLOR = 0xC8303030;
  private static final int VANILLA_KEY_COLOR = 0xFF000000;
  private static final int VANILLA_BORDER_COLOR = 0xFF999999;
  private static final int VANILLA_FOCUS_BORDER_COLOR = 0xFFFFFFFF;
  private static final int VANILLA_TEXT_COLOR = 0xFFFFFFFF;
  private static final int VANILLA_SECONDARY_TEXT_COLOR = 0xFFAAAAAA;
  private static final int VANILLA_ACCENT_COLOR = 0xFF008FE8;
  private static final int VANILLA_SEGMENT_HOVER_COLOR = 0xDCFFFFFF;
  private static final int VANILLA_SEGMENT_HOVER_TEXT_COLOR = 0xFF000000;
  private static final int VANILLA_SWITCH_KNOB_OFF_COLOR = 0xFFA0A0A0;
  private static final int VANILLA_SELECTION_COLOR = 0xFF0000FF;

  private static final WorldMapTheme INSTANCE = new WorldMapTheme();

  private final Variable background = new Variable("--background-color", 0xA30D0E0F);
  private final Variable backgroundBorder = new Variable("--background-color-border", 0xB90D0E0F);
  private final Variable button = new Variable("--button-color", 0x1EE4E6E9);
  private final Variable buttonHover = new Variable("--button-color-hover", 0x32E4E6E9);
  private final Variable accent = new Variable("--accent-button-color", 0xFF0C68B0);
  private boolean fancy;

  private WorldMapTheme() {
  }

  static WorldMapTheme get() {
    return INSTANCE;
  }

  void refresh(Window window) {
    this.fancy = FANCY_THEME_ID.equals(Laby.labyAPI().themeService().currentTheme().getId());
    if (!this.fancy) {
      return;
    }

    this.background.refresh(window);
    this.backgroundBorder.refresh(window);
    this.button.refresh(window);
    this.buttonHover.refresh(window);
    this.accent.refresh(window);
  }

  int panelColor() {
    return this.fancy ? this.background.color : VANILLA_PANEL_COLOR;
  }

  int panelEdgeColor() {
    return this.fancy ? this.backgroundBorder.color : VANILLA_BORDER_COLOR;
  }

  /**
   * @param fancyScale  the text scale of the fancy look
   * @param vanillaSize the size of the vanilla look, predefined sizes keep the pixel font on whole
   *                    physical pixels
   */
  float textScale(float fancyScale, PredefinedFontSize vanillaSize) {
    if (this.fancy) {
      return fancyScale;
    }

    return vanillaSize.value((int) Laby.labyAPI().minecraft().minecraftWindow().getScale());
  }

  int textColor() {
    return this.fancy ? FANCY_TEXT_COLOR : VANILLA_TEXT_COLOR;
  }

  int secondaryTextColor() {
    return this.fancy ? FANCY_SECONDARY_TEXT_COLOR : VANILLA_SECONDARY_TEXT_COLOR;
  }

  /**
   * @return the options for text on a panel, the vanilla look keeps the text shadow
   */
  int textOptions() {
    return this.fancy ? TextRenderingOptions.NONE : TextRenderingOptions.SHADOW;
  }

  int selectionColor() {
    return this.fancy ? FANCY_SELECTION_COLOR : VANILLA_SELECTION_COLOR;
  }

  int segmentHoverColor() {
    return this.fancy ? this.accent.color : VANILLA_SEGMENT_HOVER_COLOR;
  }

  int segmentHoverTextColor() {
    return this.fancy ? FANCY_TEXT_COLOR : VANILLA_SEGMENT_HOVER_TEXT_COLOR;
  }

  int switchTrackColor(boolean on) {
    if (on) {
      return this.fancy ? FANCY_SWITCH_ON_COLOR : VANILLA_ACCENT_COLOR;
    }

    return this.fancy ? this.button.color : VANILLA_KEY_COLOR;
  }

  int switchKnobColor(boolean on) {
    return this.fancy || on ? 0xFFFFFFFF : VANILLA_SWITCH_KNOB_OFF_COLOR;
  }

  boolean roundSwitch() {
    return this.fancy;
  }

  /**
   * Panel behind text, like tooltips and hints.
   */
  void panel(ScreenCanvas canvas, float x, float y, float width, float height, int alpha) {
    this.box(canvas, x, y, width, height, withAlpha(this.panelColor(), alpha), 0);
  }

  /**
   * Key cap of a shortcut hint.
   */
  void key(ScreenCanvas canvas, float x, float y, float width, float height, int alpha) {
    int fill = this.fancy ? this.button.color : VANILLA_KEY_COLOR;
    this.box(canvas, x, y, width, height, withAlpha(fill, alpha), withAlpha(VANILLA_BORDER_COLOR, alpha));
  }

  /**
   * Clickable state indicator, highlighted while active.
   */
  void pill(ScreenCanvas canvas, float x, float y, float width, float height, boolean active, int alpha) {
    int fill = this.fancy && active ? this.accent.color : this.panelColor();
    int border = active ? VANILLA_FOCUS_BORDER_COLOR : VANILLA_BORDER_COLOR;
    this.box(canvas, x, y, width, height, withAlpha(fill, alpha), withAlpha(border, alpha));
  }

  /**
   * Text input drawn on the canvas.
   */
  void field(ScreenCanvas canvas, float x, float y, float width, float height) {
    int fill = this.fancy ? this.background.color : VANILLA_KEY_COLOR;
    this.box(canvas, x, y, width, height, fill, VANILLA_FOCUS_BORDER_COLOR);
  }

  /**
   * Tab attached to the right edge of the atlas panel.
   */
  void handle(ScreenCanvas canvas, float x, float y, float width, float height, boolean hovered) {
    if (this.fancy) {
      int fill = hovered ? this.buttonHover.color : this.background.color;
      canvas.submitRelativeRoundedRect(x, y, width, height, fill, RIGHT_ROUNDED);
      return;
    }

    canvas.submitRelativeRect(x, y, width, height, hovered ? VANILLA_PANEL_HOVER_COLOR : VANILLA_PANEL_COLOR);
    canvas.submitRelativeRect(x, y, width, 1.0F, VANILLA_BORDER_COLOR);
    canvas.submitRelativeRect(x, y + height - 1.0F, width, 1.0F, VANILLA_BORDER_COLOR);
    canvas.submitRelativeRect(x + width - 1.0F, y + 1.0F, 1.0F, height - 2.0F, VANILLA_BORDER_COLOR);
  }

  /**
   * @param border outline of the vanilla look, {@code 0} for none
   */
  private void box(ScreenCanvas canvas, float x, float y, float width, float height, int fill, int border) {
    if (this.fancy) {
      canvas.submitRelativeRoundedRect(x, y, width, height, fill, ROUNDED);
      return;
    }

    canvas.submitRelativeRect(x, y, width, height, fill);
    if (border == 0) {
      return;
    }

    canvas.submitRelativeRect(x, y, width, 1.0F, border);
    canvas.submitRelativeRect(x, y + height - 1.0F, width, 1.0F, border);
    canvas.submitRelativeRect(x, y + 1.0F, 1.0F, height - 2.0F, border);
    canvas.submitRelativeRect(x + width - 1.0F, y + 1.0F, 1.0F, height - 2.0F, border);
  }

  static int withAlpha(int color, int alpha) {
    return ((color >>> 24) * alpha / 255) << 24 | (color & 0xFFFFFF);
  }

  /**
   * Color variable of the fancy theme, parsed again only when the variable changes.
   */
  private static final class Variable {

    private final String key;
    private final int fallback;
    @Nullable
    private LssVariable variable;
    private int color;

    private Variable(String key, int fallback) {
      this.key = key;
      this.fallback = fallback;
      this.color = fallback;
    }

    private void refresh(Window window) {
      LssVariable variable = window.getVariable(this.key);
      if (variable == this.variable) {
        return;
      }

      this.variable = variable;
      this.color = variable == null ? this.fallback : this.parse(variable.value());
    }

    private int parse(String value) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException exception) {
        return this.fallback;
      }
    }
  }
}
