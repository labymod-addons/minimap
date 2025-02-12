package net.labymod.addons.minimap.hudwidget;

import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.map.MinimapCardinalType;
import net.labymod.addons.minimap.api.map.MinimapCircle;
import net.labymod.addons.minimap.api.map.MinimapDisplayType;
import net.labymod.addons.minimap.config.MinimapConfiguration;
import net.labymod.addons.minimap.map.MinimapTexture;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.GFXBridge;
import net.labymod.api.client.gfx.pipeline.GFXRenderPipeline;
import net.labymod.api.client.gfx.pipeline.pass.passes.StencilRenderPass;
import net.labymod.api.client.gui.hud.binding.category.HudWidgetCategory;
import net.labymod.api.client.gui.hud.hudwidget.HudWidget;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.widget.widgets.hud.HudWidgetWidget;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.configuration.loader.annotation.SpriteSlot;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;

@SpriteSlot(size = 32)
public class MinimapHudWidget extends HudWidget<MinimapHudWidgetConfig> {

  private final MinimapRenderEvent renderEvent = new MinimapRenderEvent();

  private final MinimapAddon addon;

  private final MinimapCircle circle = new MinimapCircle();

  private final MinimapRenderer renderer;
  private final StencilRenderPass stencilRenderPass = new StencilRenderPass();

  private MinimapTexture texture;

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

    if (this.texture != null) {
      this.texture.release();
    }

    this.texture = new MinimapTexture(this.addon, config);
    this.texture.init();

    this.renderer.initialize();
  }

  @Override
  public void updateSize(HudWidgetWidget widget, boolean isEditorContext, HudSize size) {
    size.set(150F, 150F);
  }

  @Override
  public void render(
      Stack stack, MutableMouse mouse, float partialTicks, boolean isEditorContext, HudSize size) {
    if (!this.labyAPI.minecraft().isIngame()) {
      this.renderMapOutline(stack, size, null);
      return;
    }

    ClientPlayer player = this.labyAPI.minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    float radius = size.getActualWidth() / 2F;
    if (this.lastRadius != radius) {
      this.distanceToCorner = (float) Math.sqrt(radius * radius + radius * radius);
      this.lastRadius = radius;
    }

    this.renderEvent.fill(stack, size, this.texture.getCurrentBounds(), this.circle);
    MinimapConfiguration configuration = this.configuration();
    this.circle.init(configuration.displayType().get(), size, this.distanceToCorner);

    GFXRenderPipeline renderPipeline = this.labyAPI.gfxRenderPipeline();
    GFXBridge gfx = renderPipeline.gfx();

    this.renderMapOutline(stack, size, MinimapDisplayType.Stage.BEFORE_TEXTURE);
    renderPipeline.renderToActivityTarget(target -> {
      gfx.enableStencil();
      renderPipeline.clear(target);

      this.stencilRenderPass.begin();
      configuration.displayType().get().renderStencil(stack, radius);
      this.stencilRenderPass.end();

      // Render minimap
      if (this.addon.isMinimapAllowed()) {
        stack.push();

        this.applyZoom(player, stack, size, true);
        this.renderMapTexture(player, stack, size);

        this.renderEvent.fireWithStage(Stage.ROTATED_STENCIL);

        stack.pop();
      }

      stack.push();
      this.applyZoom(player, stack, size, false);
      this.renderEvent.fireWithStage(Stage.STRAIGHT_ZOOMED_STENCIL);
      stack.pop();

      this.renderEvent.fireWithStage(Stage.STRAIGHT_NORMAL_STENCIL);

      gfx.disableStencil();
    });
    renderPipeline.clear(renderPipeline.getActivityRenderTarget());

    this.renderMapOutline(stack, size, MinimapDisplayType.Stage.AFTER_TEXTURE);

    if (configuration.cardinalType().get() != MinimapCardinalType.HIDDEN) {
      stack.push();
      stack.translate(0F, 0F, 400.0F);

      this.renderCardinals(player, stack);

      stack.pop();
    }

    if (this.addon.isMinimapAllowed()) {
      stack.push();
      this.applyZoom(player, stack, size, true);
      this.renderEvent.fireWithStage(Stage.ROTATED);
      stack.pop();
    }

    stack.push();
    this.applyZoom(player, stack, size, false);
    this.renderEvent.fireWithStage(Stage.STRAIGHT_ZOOMED);
    stack.pop();

    this.renderEvent.fireWithStage(Stage.STRAIGHT_NORMAL);
  }

  private void applyZoom(ClientPlayer player, Stack stack, HudSize size, boolean rotate) {
    double addZoom = this.distanceToCorner / this.lastRadius + 0.3D;

    Position position = player.position();
    Position previousPosition = player.previousPosition();

    MinimapConfiguration configuration = this.configuration();
    if (configuration.jumpBouncing().get()) {
      addZoom += (previousPosition.getY() - position.getY()) / 20.0D;
    }

    if (configuration.autoZoom().get()) {
      double distanceToFloor = (position.getY() - this.texture.getAnimatedHighestBlockY());
      if (distanceToFloor < -70) {
        distanceToFloor = -70;
      }
      addZoom -= (distanceToFloor / (100D + distanceToFloor));
    }

    // Rotate and scale map
    stack.translate(size.getActualWidth() / 2F, size.getActualHeight() / 2F, 0F);
    if (rotate) {
      stack.scale(-1, 1, 1);
      stack.rotate(player.getRotationHeadYaw(), 0F, 0F, 1F);
    }
    stack.scale((float) addZoom, (float) addZoom, 1F);
    stack.translate(-size.getActualWidth() / 2F, -size.getActualHeight() / 2F, 0F);

    this.renderEvent.setZoom((float) addZoom);
  }

  private void renderMapTexture(ClientPlayer player, Stack stack, HudSize size) {
    MinimapBounds bounds = this.renderer.minimapBounds();
    float mapMidX = bounds.getX1() + (bounds.getX2() - bounds.getX1()) / 2F;
    float mapMidZ = bounds.getZ1() + (bounds.getZ2() - bounds.getZ1()) / 2F;

    Position position = player.position();
    Position previousPosition = player.previousPosition();
    double smoothX = MathHelper.lerp(position.getX(), previousPosition.getX());
    double smoothZ = MathHelper.lerp(position.getZ(), previousPosition.getZ());

    smoothX -= mapMidX;
    smoothZ -= mapMidZ;

    MinimapConfiguration configuration = this.configuration();
    float pixelLength = size.getActualWidth() / (configuration.zoom().get() * 10F) / 2F;
    double offsetX = -pixelLength * smoothX;
    double offsetZ = -pixelLength * smoothZ;

    float pixelWidthX = -0.4F;
    float pixelWidthY = -0.4F;

    this.renderEvent.setPixelLength(pixelLength);

    GFXBridge gfx = Laby.gfx();
    gfx.storeBlaze3DStates();
    gfx.disableCull();

    stack.push();
    stack.translate(
        pixelWidthX + offsetX,
        -(pixelWidthY + offsetZ),
        0F
    );

    this.renderer.render(
        stack,
        0,
        0,
        size.getActualWidth(),
        size.getActualHeight()
    );
    stack.pop();
    gfx.restoreBlaze3DStates();
  }

  private void renderMapOutline(Stack stack, HudSize size, MinimapDisplayType.Stage stage) {
    MinimapConfiguration configuration = this.configuration();
    MinimapDisplayType displayType = configuration.displayType().get();
    if (stage != null && displayType.stage() != stage) {
      return;
    }

    displayType.icon().render(
        stack,
        -MinimapHudWidgetConfig.BORDER_PADDING,
        -MinimapHudWidgetConfig.BORDER_PADDING,
        size.getActualWidth() + MinimapHudWidgetConfig.BORDER_PADDING * 2F,
        size.getActualHeight() + MinimapHudWidgetConfig.BORDER_PADDING * 2F
    );
  }

  private void renderCardinals(ClientPlayer player, Stack stack) {
    MinimapConfiguration configuration = this.configuration();
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
        this.labyAPI.renderPipeline()
            .textRenderer()
            .pos(this.circle.getCircleX() - 2, this.circle.getCircleY() - 4)
            .text(cardinal)
            .render(stack);
      }

      index++;
    }
  }

  private MinimapConfiguration configuration() {
    return this.renderer.configuration();
  }

}
