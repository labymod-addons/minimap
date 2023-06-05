package net.labymod.addons.minimap.hudwidget;

import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.config.MinimapCardinalType;
import net.labymod.addons.minimap.config.MinimapDisplayType;
import net.labymod.addons.minimap.config.MinimapUpdateMethod;
import net.labymod.addons.minimap.event.DefaultMinimapRenderEvent;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.map.MinimapTexture;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.GFXBridge;
import net.labymod.api.client.gfx.pipeline.pass.passes.StencilRenderPass;
import net.labymod.api.client.gui.hud.hudwidget.HudWidget;
import net.labymod.api.client.gui.hud.hudwidget.HudWidgetConfig;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.widget.widgets.hud.HudWidgetWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.SliderWidget.SliderSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownEntryTranslationPrefix;
import net.labymod.api.client.gui.screen.widget.widgets.input.dropdown.DropdownWidget.DropdownSetting;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.util.math.MathHelper;

public class MinimapHudWidget extends HudWidget<MinimapHudWidgetConfig> {

  public static final float BORDER_PADDING = 5F;

  private final DefaultMinimapRenderEvent renderEvent = new DefaultMinimapRenderEvent();

  private MinimapTexture texture;

  private float distanceToCorner = 0;
  private float lastRadius = 0;
  private float lastZoom = 0;

  private final StencilRenderPass stencilRenderPass = new StencilRenderPass();

  public MinimapHudWidget() {
    super("minimap", MinimapHudWidgetConfig.class);
  }

  @Override
  public boolean isVisibleInGame() {
    return true;
  }

  @Override
  public void load(MinimapHudWidgetConfig config) {
    super.load(config);

    if (this.texture != null) {
      this.texture.release();
    }

    this.texture = new MinimapTexture(config);
    this.texture.init();
  }

  @Override
  public void updateSize(HudWidgetWidget widget, boolean isEditorContext, HudSize size) {
    size.set(150, 150);
  }

  @Override
  public void render(
      Stack stack, MutableMouse mouse, float partialTicks, boolean isEditorContext, HudSize size) {
    if (!this.labyAPI.minecraft().isIngame()) {
      this.renderMapOutline(stack, size);
      return;
    }

    stack.push();

    ClientPlayer player = this.labyAPI.minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    this.renderEvent.fill(stack, size, this.texture.getCurrentBounds());

    float radius = size.getWidth() / 2F;
    if (this.lastRadius != radius) {
      this.distanceToCorner = (float) Math.sqrt(radius * radius + radius * radius);
      this.lastRadius = radius;
    }

    GFXBridge gfx = this.labyAPI.gfxRenderPipeline().gfx();

    this.labyAPI.gfxRenderPipeline().renderToActivityTarget(target -> {
      gfx.enableStencil();

      this.labyAPI.gfxRenderPipeline().clear(target);

      this.stencilRenderPass.begin();
      this.config.displayType().get().stencil().render(stack, radius);
      this.stencilRenderPass.end();

      // Render minimap
      this.applyRotation(player, stack, size);
      this.renderMapTexture(player, stack, size);

      this.renderEvent.fireWithStage(Stage.ROTATED);

      gfx.disableStencil();
    });

    stack.pop();

    this.renderEvent.fireWithStage(Stage.STRAIGHT);

    this.renderMapOutline(stack, size);

    if (this.config.cardinalType().get() != MinimapCardinalType.HIDDEN) {
      stack.push();
      stack.translate(0F, 0F, 400.0F);

      this.renderCardinals(player, stack, size);

      stack.pop();
    }
  }

  private void applyRotation(ClientPlayer player, Stack stack, HudSize size) {
    float addZoom = this.distanceToCorner / this.lastRadius + 0.3F;

    if (this.config.jumpBouncing().get()) {
      addZoom += (player.getPreviousPosY() - player.getPosY()) / 20F;
    }

    if (this.config.autoZoom().get()) {
      float distanceToFloor = (player.getPosY() - this.texture.getAnimatedHighestBlockY());
      if (distanceToFloor < -70) {
        distanceToFloor = -70;
      }
      addZoom -= distanceToFloor / (100D + distanceToFloor);
    }

    this.lastZoom = addZoom;

    // Rotate and scale map
    stack.translate(size.getWidth() / 2F, size.getHeight() / 2F, 0F);
    stack.rotate(-player.getRotationHeadYaw() + 180F, 0F, 0F, 1F);
    stack.scale(addZoom, addZoom, 1F);
    stack.translate(-size.getWidth() / 2F, -size.getHeight() / 2F, 0F);
  }

  private void renderMapTexture(ClientPlayer player, Stack stack, HudSize size) {
    if (!this.texture.isAvailable()) {
      return;
    }

    MinimapBounds bounds = this.texture.getCurrentBounds();

    float mapMidX = bounds.getX1() + (bounds.getX2() - bounds.getX1()) / 2F;
    float mapMidZ = bounds.getZ1() + (bounds.getZ2() - bounds.getZ1()) / 2F;

    float smoothX = MathHelper.lerp(player.getPosX(), player.getPreviousPosX());
    float smoothZ = MathHelper.lerp(player.getPosZ(), player.getPreviousPosZ());

    smoothX -= mapMidX;
    smoothZ -= mapMidZ;

    float pixelLength = size.getWidth() / (this.config.zoom().get() * 10F) / 2F;
    float offsetX = -pixelLength * smoothX;
    float offsetZ = -pixelLength * smoothZ;

    float pixelWidthX = -0.4F;
    float pixelWidthY = -0.4F;

    this.texture.icon().render(
        stack,
        pixelWidthX + offsetX,
        pixelWidthY + offsetZ,
        size.getWidth(),
        size.getHeight()
    );
  }

  private void renderMapOutline(Stack stack, HudSize size) {
    this.config.displayType().get().icon().render(
        stack,
        -BORDER_PADDING,
        -BORDER_PADDING,
        size.getWidth() + BORDER_PADDING * 2F,
        size.getHeight() + BORDER_PADDING * 2F
    );
  }

  private void renderCardinals(ClientPlayer player, Stack stack, HudSize size) {
    MinimapCardinalType type = this.config.cardinalType().get();
    boolean numbers = type == MinimapCardinalType.NUMBERS;
    String[] cardinals = numbers
        ? new String[]{"2", "3", "0", "1"}
        : new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    float yaw = player.getRotationHeadYaw();
    float radius = size.getWidth() / 2F;

    boolean circle = this.config.displayType().get() == MinimapDisplayType.ROUND;

    float rotCenterX = size.getWidth() / 2F;
    float rotCenterY = size.getHeight() / 2F;

    int index = 1;
    for (String cardinal : cardinals) {
      double f =
          Math.PI / 360 * ((yaw - index * (numbers ? 90 : 45)) % 360 - (numbers ? 0 : 45)) * 2;

      float offsetCos = (float) Math.cos(-f);
      float offsetSin = (float) Math.sin(-f);

      offsetCos *= circle ? radius : this.distanceToCorner;
      offsetSin *= circle ? radius : this.distanceToCorner;

      if (offsetCos < -radius) {
        offsetCos = -radius;
      }
      if (offsetSin < -radius) {
        offsetSin = -radius;
      }

      if (offsetCos > radius) {
        offsetCos = radius;
      }
      if (offsetSin > radius) {
        offsetSin = radius;
      }

      float circleX = rotCenterX + offsetCos;
      float circleY = rotCenterY + offsetSin;

      if (numbers || index % 2 == 1 || type == MinimapCardinalType.EXTENDED) {
        this.labyAPI.renderPipeline()
            .textRenderer()
            .pos(circleX, circleY - 4)
            .text(cardinal)
            .render(stack);
      }

      index++;
    }
  }

  public static class MinimapHudWidgetConfig extends HudWidgetConfig {

    // TODO: Round is currently not working when scaled @DropdownSetting
    private static final ConfigProperty<MinimapDisplayType> SQUARE =
        new ConfigProperty<>(MinimapDisplayType.SQUARE);

    @DropdownEntryTranslationPrefix("labysminimap.hudWidget.minimap.displayType.entries")
    private final ConfigProperty<MinimapDisplayType> displayType =
        new ConfigProperty<>(MinimapDisplayType.ROUND);
    @SwitchSetting
    private final ConfigProperty<Boolean> jumpBouncing = new ConfigProperty<>(false);
    @SwitchSetting
    private final ConfigProperty<Boolean> autoZoom = new ConfigProperty<>(false);
    @SliderSetting(min = 2, max = 30)
    private final ConfigProperty<Integer> zoom = new ConfigProperty<>(12);
    @DropdownSetting
    @DropdownEntryTranslationPrefix("labysminimap.hudWidget.minimap.updateMethod.entries")
    private final ConfigProperty<MinimapUpdateMethod> updateMethod =
        new ConfigProperty<>(MinimapUpdateMethod.CHUNK_TRIGGER);
    @DropdownSetting
    @DropdownEntryTranslationPrefix("labysminimap.hudWidget.minimap.cardinalType.entries")
    private final ConfigProperty<MinimapCardinalType> cardinalType
        = new ConfigProperty<>(MinimapCardinalType.NORMAL);

    public ConfigProperty<MinimapDisplayType> displayType() {
      return SQUARE;
    }

    public ConfigProperty<Boolean> jumpBouncing() {
      return this.jumpBouncing;
    }

    public ConfigProperty<Boolean> autoZoom() {
      return this.autoZoom;
    }

    public ConfigProperty<Integer> zoom() {
      return this.zoom;
    }

    public ConfigProperty<MinimapUpdateMethod> updateMethod() {
      return this.updateMethod;
    }

    public ConfigProperty<MinimapCardinalType> cardinalType() {
      return this.cardinalType;
    }
  }

}
