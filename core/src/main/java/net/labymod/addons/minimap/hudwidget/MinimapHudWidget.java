package net.labymod.addons.minimap.hudwidget;

import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.map.MinimapCardinalType;
import net.labymod.addons.minimap.api.map.MinimapCircle;
import net.labymod.addons.minimap.api.map.MinimapDisplayType;
import net.labymod.addons.minimap.api.map.MinimapPlayerIcon;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.pipeline.RenderAttributes;
import net.labymod.api.client.gfx.pipeline.RenderAttributes.StencilMode;
import net.labymod.api.client.gfx.pipeline.RenderAttributesStack;
import net.labymod.api.client.gui.hud.binding.category.HudWidgetCategory;
import net.labymod.api.client.gui.hud.hudwidget.HudWidget;
import net.labymod.api.client.gui.hud.hudwidget.HudWidgetConfig;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.state.TextFlags;
import net.labymod.api.client.gui.screen.widget.widgets.hud.HudWidgetWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.color.ColorPickerWidget.ColorPickerSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownEntryTranslationPrefix;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.configuration.loader.annotation.SpriteSlot;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.configuration.settings.annotation.SettingSection;
import net.labymod.api.util.Color;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;

@SpriteSlot(size = 32)
public class MinimapHudWidget extends HudWidget<MinimapHudWidgetConfig> {

  private static final float DEFAULT_MINIMAP_WIDTH = 150.0F;
  private static final float DEFAULT_MINIMAP_HEIGHT = 150.0F;
  private static final float DEFAULT_TEXT_LINE_HEIGHT = 10.0F;

  private final MinimapRenderEvent renderEvent = new MinimapRenderEvent();

  private final MinimapAddon addon;

  private final MinimapCircle circle = new MinimapCircle();

  private final MinimapRenderer renderer;

  private float distanceToCorner = 0;
  private float lastRadius = 0;


  public MinimapHudWidget(MinimapAddon addon, MinimapRenderer renderer) {
    super("minimap", MinimapHudWidgetConfig.class);

    this.bindCategory(HudWidgetCategory.INGAME);

    this.addon = addon;

    this.renderer = renderer;
  }

  @Override
  public boolean isVisibleInGame() {
    return this.addon.isMinimapAllowed();
  }

  @Override
  public void load(MinimapHudWidgetConfig config) {
    super.load(config);

    this.renderer.initialize();
  }

  @Override
  public void updateSize(HudWidgetWidget widget, boolean isEditorContext, HudSize size) {
    size.set(DEFAULT_MINIMAP_WIDTH, DEFAULT_MINIMAP_HEIGHT);
    if (this.config.showCoordinates().get()) {
      size.set(DEFAULT_MINIMAP_WIDTH, DEFAULT_MINIMAP_HEIGHT + DEFAULT_TEXT_LINE_HEIGHT);
    }

  }

  @Override
  public void render(ScreenContext context, boolean isEditorContext, HudSize size) {
    HudSize newHudSize = size;
    boolean showCoordinates = this.config.showCoordinates().get();
    if (showCoordinates) {
      newHudSize = newHudSize.copy();
      newHudSize.setHeight(newHudSize.getActualHeight() - DEFAULT_TEXT_LINE_HEIGHT);
    }

    if (!this.labyAPI.minecraft().isIngame()) {
      this.renderMapOutline(context, newHudSize, null);
      return;
    }

    ClientPlayer player = this.labyAPI.minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    Position position = player.position();

    ScreenCanvas canvas = context.canvas();
    if (showCoordinates) {
      String coordinatesText = "X: "
          + MathHelper.floor(position.getX())
          + " Y: "
          + MathHelper.floor(position.getY())
          + " Z: "
          + MathHelper.floor(position.getZ());
      canvas.submitText(
          coordinatesText,
          newHudSize.getActualWidth() / 2.0F,
          newHudSize.getActualHeight(),
          -1,
          1.0F,
          TextFlags.SHADOW | TextFlags.CENTERED
      );
    }

    context.pushStack();
    context.translate(newHudSize.getActualWidth() / 2F, newHudSize.getActualHeight() / 2F, 0F);
    context.scale(Util.MINIMAP_SCALE, Util.MINIMAP_SCALE, 1F);
    context.translate(-newHudSize.getActualWidth() / 2F, -newHudSize.getActualHeight() / 2F, 0F);
    float radius = newHudSize.getActualWidth() / 2F;
    if (this.lastRadius != radius) {
      this.distanceToCorner = (float) Math.sqrt(radius * radius + radius * radius);
      this.lastRadius = radius;
    }

    this.renderEvent.fill(context, newHudSize, new MinimapBounds(), this.circle);
    var configuration = this.configuration();
    this.circle.init(configuration.displayType().get(), newHudSize, this.distanceToCorner);

    this.renderMapOutline(context, newHudSize, MinimapDisplayType.Stage.BEFORE_TEXTURE);

    RenderAttributesStack renderAttributesStack = Laby.references().renderEnvironmentContext()
        .renderAttributesStack();
    RenderAttributes renderAttributes = renderAttributesStack.pushAndGet();
    renderAttributes.setStencilMode(StencilMode.WRITE_STENCIL);
    configuration.displayType().get().renderStencil(context, radius);
    renderAttributes.setStencilMode(StencilMode.WRITE_TO_STENCIL);

    // Render minimap
    if (this.addon.isMinimapAllowed()) {
      context.pushStack();

      this.applyZoom(player, context, newHudSize, true);
      this.renderMapTexture(player, context, newHudSize);

      this.renderEvent.fireWithStage(Stage.ROTATED_STENCIL);

      context.popStack();
    }

    context.pushStack();
    this.applyZoom(player, context, newHudSize, false);
    this.renderEvent.fireWithStage(Stage.STRAIGHT_ZOOMED_STENCIL);
    context.popStack();

    this.renderEvent.fireWithStage(Stage.STRAIGHT_NORMAL_STENCIL);
    renderAttributesStack.pop();

    this.renderMapOutline(context, newHudSize, MinimapDisplayType.Stage.AFTER_TEXTURE);

    if (configuration.cardinalType().get() != MinimapCardinalType.HIDDEN) {
      this.renderCardinals(player, context);
    }

    if (this.addon.isMinimapAllowed()) {
      context.pushStack();
      this.applyZoom(player, context, newHudSize, true);
      this.renderEvent.fireWithStage(Stage.ROTATED);
      context.popStack();
    }

    context.pushStack();
    this.applyZoom(player, context, newHudSize, false);
    this.renderEvent.fireWithStage(Stage.STRAIGHT_ZOOMED);
    context.popStack();

    this.renderEvent.fireWithStage(Stage.STRAIGHT_NORMAL);
    context.popStack();
  }

  @Override
  public void onTick(boolean isEditorContext) {
    super.onTick(isEditorContext);
    this.renderer.tick();
  }

  private void applyZoom(ClientPlayer player, ScreenContext context, HudSize size, boolean rotate) {
    Stack stack = context.stack();
    double addZoom = this.distanceToCorner / this.lastRadius + 0.3D;

    Position position = player.position();
    Position previousPosition = player.previousPosition();

    var configuration = this.configuration();
    if (configuration.jumpBouncing().get()) {
      addZoom += (previousPosition.getY() - position.getY()) / 20.0D;
    }

    // Rotate and scale map
    stack.translate(size.getActualWidth() / 2F, size.getActualHeight() / 2F, 0F);
    if (rotate) {
      stack.scale(-1, -1, 1);
      stack.rotate(-player.getRotationHeadYaw(), 0F, 0F, 1F);
    }
    stack.scale((float) addZoom, (float) addZoom, 1F);
    stack.translate(-size.getActualWidth() / 2F, -size.getActualHeight() / 2F, 0F);

    this.renderEvent.setZoom((float) addZoom);
  }

  private void renderMapTexture(ClientPlayer player, ScreenContext context, HudSize size) {
    Stack stack = context.stack();

    // Use current storage bounds so movement is continuous relative to the active map window
    MinimapBounds bounds = this.renderer.minimapBounds();
    float mapMidX = bounds.getX1() + (bounds.getX2() - bounds.getX1()) / 2F;
    float mapMidZ = bounds.getZ1() + (bounds.getZ2() - bounds.getZ1()) / 2F;

    // Smooth player position (interpolated)
    Position position = player.position();
    Position previousPosition = player.previousPosition();
    double smoothX = position.lerpX(previousPosition, context.getTickDelta());
    double smoothZ = position.lerpZ(previousPosition, context.getTickDelta());

    // Offset from the map window center to the player (in blocks)
    double dx = smoothX - mapMidX;
    double dz = smoothZ - mapMidZ;

    // Pixels-per-block for current zoom
    int zoomBlocks = this.configuration().zoom().get() * 10;
    float pixelsPerBlock = size.getActualWidth() / (zoomBlocks * 2.0F);
    this.renderEvent.setPixelLength(pixelsPerBlock);

    // Convert world offset to screen pixels (screen Y grows downward, hence +dz)
    double offsetX = -pixelsPerBlock * dx;
    double offsetY = +pixelsPerBlock * dz;

    // Small texel-alignment tweak to avoid sampling seams
    float pixelNudgeX = -0.4F;
    float pixelNudgeY = -0.4F;

    stack.push();
    stack.translate(
        (float) (pixelNudgeX + offsetX),
        (float) -(pixelNudgeY + offsetY),
        0F
    );

    // Render once; centering and smooth movement are driven by the translation above
    this.renderer.render(
        context,
        0,
        0,
        size.getActualWidth(),
        size.getActualHeight()
    );
    stack.pop();


  }

  private void renderMapOutline(ScreenContext context, HudSize size,
      MinimapDisplayType.Stage stage) {
    var configuration = this.configuration();
    MinimapDisplayType displayType = configuration.displayType().get();
    if (stage != null && displayType.stage() != stage) {
      return;
    }

    context.canvas().submitIcon(
        displayType.icon(),
        -Util.BORDER_PADDING,
        -Util.BORDER_PADDING,
        size.getActualWidth() + Util.BORDER_PADDING * 2F,
        size.getActualHeight() + Util.BORDER_PADDING * 2F
    );
  }

  private void renderCardinals(ClientPlayer player, ScreenContext context) {
    var configuration = this.configuration();
    MinimapCardinalType type = configuration.cardinalType().get();
    boolean numbers = type == MinimapCardinalType.NUMBERS;
    String[] cardinals = numbers
        ? new String[]{"2", "3", "0", "1"}
        : new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    float yaw = player.getRotationHeadYaw();

    int index = 1;
    for (String cardinal : cardinals) {
      double f =
          Math.PI / 360 * ((yaw - index * (numbers ? 90 : 45)) % 360 - (numbers ? 0 : 45)) * 2;
      this.circle.calculate(f);

      if (numbers || index % 2 == 1 || type == MinimapCardinalType.EXTENDED) {
        context.canvas().submitText(
            cardinal,
            this.circle.getCircleX() - 2, this.circle.getCircleY() - 4,
            -1,
            1.0F,
            TextFlags.SHADOW
        );
      }

      index++;
    }
  }

  private MinimapHudWidgetConfig configuration() {
    return this.getConfig();
  }

  public static class MinimapHudWidgetConfig
      extends HudWidgetConfig
      implements net.labymod.addons.minimap.api.config.MinimapHudWidgetConfig {

    @DropdownEntryTranslationPrefix("labysminimap.hudWidget.minimap.displayType.entries")
    @DropdownSetting
    private final ConfigProperty<MinimapDisplayType> displayType = ConfigProperty.createEnum(
        MinimapDisplayType.ROUND_FUTURE_THIN
    );

    @SwitchSetting
    private final ConfigProperty<Boolean> jumpBouncing = ConfigProperty.create(false);

    @SliderSetting(min = 2, max = 30)
    private final ConfigProperty<Integer> zoom = ConfigProperty.create(12);

    @DropdownSetting
    @DropdownEntryTranslationPrefix(Util.NAMESPACE + ".hudWidget.minimap.cardinalType.entries")
    private final ConfigProperty<MinimapCardinalType> cardinalType = ConfigProperty.create(
        MinimapCardinalType.NORMAL);

    @SwitchSetting
    private final ConfigProperty<Boolean> showCoordinates = ConfigProperty.create(false);

    @SettingSection("tiles")
    @SliderSetting(min = 4, max = 16)
    private final ConfigProperty<Integer> tileSize = ConfigProperty.create(12);

    @SwitchSetting
    private final ConfigProperty<Boolean> showOwnPlayer = ConfigProperty.create(true);
    @SwitchSetting
    private final ConfigProperty<Boolean> showPlayers = ConfigProperty.create(true);
    @SwitchSetting
    private final ConfigProperty<Boolean> showWaypoints = ConfigProperty.create(true);

    @SettingSection("player")
    @DropdownSetting
    private final ConfigProperty<MinimapPlayerIcon> playerIcon = ConfigProperty.create(
        MinimapPlayerIcon.TRIANGLE);

    @ColorPickerSetting(alpha = true, chroma = true)
    private final ConfigProperty<Color> playerColor = ConfigProperty.create(Color.WHITE);

    @Override
    public ConfigProperty<MinimapDisplayType> displayType() {
      return this.displayType;
    }

    @Override
    public ConfigProperty<Boolean> jumpBouncing() {
      return this.jumpBouncing;
    }

    @Override
    public ConfigProperty<Integer> zoom() {
      return this.zoom;
    }

    public ConfigProperty<Boolean> showCoordinates() {
      return this.showCoordinates;
    }

    @Override
    public ConfigProperty<Integer> tileSize() {
      return this.tileSize;
    }

    @Override
    public ConfigProperty<Boolean> showOwnPlayer() {
      return this.showOwnPlayer;
    }

    @Override
    public ConfigProperty<Boolean> showPlayers() {
      return this.showPlayers;
    }

    @Override
    public ConfigProperty<Boolean> showWaypoints() {
      return this.showWaypoints;
    }

    @Override
    public ConfigProperty<MinimapCardinalType> cardinalType() {
      return this.cardinalType;
    }

    @Override
    public ConfigProperty<MinimapPlayerIcon> playerIcon() {
      return this.playerIcon;
    }

    @Override
    public ConfigProperty<Color> playerColor() {
      return this.playerColor;
    }
  }

}
