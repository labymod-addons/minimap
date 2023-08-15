package net.labymod.addons.minimap.hudwidget;

import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.map.MinimapCardinalType;
import net.labymod.addons.minimap.api.map.MinimapCircle;
import net.labymod.addons.minimap.map.MinimapTexture;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.GFXBridge;
import net.labymod.api.client.gfx.pipeline.pass.passes.StencilRenderPass;
import net.labymod.api.client.gui.hud.binding.category.HudWidgetCategory;
import net.labymod.api.client.gui.hud.hudwidget.HudWidget;
import net.labymod.api.client.gui.hud.position.HudSize;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.widget.widgets.hud.HudWidgetWidget;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.configuration.loader.annotation.SpriteSlot;
import net.labymod.api.util.math.MathHelper;

@SpriteSlot(size = 32)
public class MinimapHudWidget extends HudWidget<MinimapHudWidgetConfig> {

  private final MinimapRenderEvent renderEvent = new MinimapRenderEvent();

  private final MinimapAddon addon;

  private final MinimapCircle circle = new MinimapCircle();

  private MinimapTexture texture;

  private float distanceToCorner = 0;
  private float lastRadius = 0;

  private final StencilRenderPass stencilRenderPass = new StencilRenderPass();

  public MinimapHudWidget(MinimapAddon addon) {
    super("minimap", MinimapHudWidgetConfig.class);

    this.bindCategory(HudWidgetCategory.INGAME);

    this.addon = addon;
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

    ClientPlayer player = this.labyAPI.minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    float radius = size.getWidth() / 2F;
    if (this.lastRadius != radius) {
      this.distanceToCorner = (float) Math.sqrt(radius * radius + radius * radius);
      this.lastRadius = radius;
    }

    this.renderEvent.fill(stack, size, this.texture.getCurrentBounds(), this.circle);
    this.circle.init(this.config.displayType().get(), size, this.distanceToCorner);

    GFXBridge gfx = this.labyAPI.gfxRenderPipeline().gfx();

    this.labyAPI.gfxRenderPipeline().renderToActivityTarget(target -> {
      gfx.enableStencil();

      this.labyAPI.gfxRenderPipeline().clear(target);

      this.stencilRenderPass.begin();
      this.config.displayType().get().stencil().render(stack, radius);
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

    this.renderMapOutline(stack, size);

    if (this.config.cardinalType().get() != MinimapCardinalType.HIDDEN) {
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

    // Rotate and scale map
    stack.translate(size.getWidth() / 2F, size.getHeight() / 2F, 0F);
    if (rotate) {
      stack.rotate(-player.getRotationHeadYaw() + 180F, 0F, 0F, 1F);
    }
    stack.scale(addZoom, addZoom, 1F);
    stack.translate(-size.getWidth() / 2F, -size.getHeight() / 2F, 0F);

    this.renderEvent.setZoom(addZoom);
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

    this.renderEvent.setPixelLength(pixelLength);

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
        -MinimapHudWidgetConfig.BORDER_PADDING,
        -MinimapHudWidgetConfig.BORDER_PADDING,
        size.getWidth() + MinimapHudWidgetConfig.BORDER_PADDING * 2F,
        size.getHeight() + MinimapHudWidgetConfig.BORDER_PADDING * 2F
    );
  }

  private void renderCardinals(ClientPlayer player, Stack stack) {
    MinimapCardinalType type = this.config.cardinalType().get();
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
            .pos(this.circle.getCircleX(), this.circle.getCircleY() - 4)
            .text(cardinal)
            .render(stack);
      }

      index++;
    }
  }
}
