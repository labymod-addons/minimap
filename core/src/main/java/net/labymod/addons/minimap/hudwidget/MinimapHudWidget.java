package net.labymod.addons.minimap.hudwidget;

import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.map.MinimapCardinalType;
import net.labymod.addons.minimap.api.map.MinimapCircle;
import net.labymod.addons.minimap.api.map.MinimapDisplayType;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.config.MinimapConfiguration;
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
import net.labymod.api.client.gui.screen.state.TextFlags;
import net.labymod.api.client.gui.screen.widget.widgets.hud.HudWidgetWidget;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.configuration.loader.annotation.SpriteSlot;
import net.labymod.api.util.math.position.Position;

@SpriteSlot(size = 32)
public class MinimapHudWidget extends HudWidget<HudWidgetConfig> {

  private final MinimapRenderEvent renderEvent = new MinimapRenderEvent();

  private final MinimapAddon addon;

  private final MinimapCircle circle = new MinimapCircle();

  private final MinimapRenderer renderer;

  private float distanceToCorner = 0;
  private float lastRadius = 0;


  public MinimapHudWidget(MinimapAddon addon, MinimapRenderer renderer) {
    super("minimap", HudWidgetConfig.class);

    this.bindCategory(HudWidgetCategory.INGAME);

    this.addon = addon;

    this.renderer = renderer;
  }

  @Override
  public boolean isVisibleInGame() {
    return this.addon.isMinimapAllowed();
  }

  @Override
  public void load(HudWidgetConfig config) {
    super.load(config);

    this.renderer.initialize();
  }

  @Override
  public void updateSize(HudWidgetWidget widget, boolean isEditorContext, HudSize size) {
    size.set(150F, 150F);
  }


  @Override
  public void render(ScreenContext context, boolean isEditorContext, HudSize size) {
    if (!this.labyAPI.minecraft().isIngame()) {
      this.renderMapOutline(context, size, null);
      return;
    }

    ClientPlayer player = this.labyAPI.minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    context.pushStack();
    context.translate(size.getActualWidth() / 2F, size.getActualHeight() / 2F, 0F);
    context.scale(0.95F,0.95F, 1F);
    context.translate(-size.getActualWidth() / 2F, -size.getActualHeight() / 2F, 0F);
    float radius = size.getActualWidth() / 2F;
    if (this.lastRadius != radius) {
      this.distanceToCorner = (float) Math.sqrt(radius * radius + radius * radius);
      this.lastRadius = radius;
    }

    this.renderEvent.fill(context, size, new MinimapBounds(), this.circle);
    MinimapConfiguration configuration = this.configuration();
    this.circle.init(configuration.displayType().get(), size, this.distanceToCorner);

    this.renderMapOutline(context, size, MinimapDisplayType.Stage.BEFORE_TEXTURE);

    RenderAttributesStack renderAttributesStack = Laby.references().renderEnvironmentContext().renderAttributesStack();
    RenderAttributes renderAttributes = renderAttributesStack.pushAndGet();
    renderAttributes.setStencilMode(StencilMode.WRITE_STENCIL);
    configuration.displayType().get().renderStencil(context, radius);
    renderAttributes.setStencilMode(StencilMode.WRITE_TO_STENCIL);

    // Render minimap
    if (this.addon.isMinimapAllowed()) {
      context.pushStack();

      this.applyZoom(player, context, size, true);
      this.renderMapTexture(player, context, size);

      this.renderEvent.fireWithStage(Stage.ROTATED_STENCIL);

      context.popStack();
    }

    context.pushStack();
    this.applyZoom(player, context, size, false);
    this.renderEvent.fireWithStage(Stage.STRAIGHT_ZOOMED_STENCIL);
    context.popStack();

    this.renderEvent.fireWithStage(Stage.STRAIGHT_NORMAL_STENCIL);
    renderAttributesStack.pop();

    this.renderMapOutline(context, size, MinimapDisplayType.Stage.AFTER_TEXTURE);

    if (configuration.cardinalType().get() != MinimapCardinalType.HIDDEN) {
      this.renderCardinals(player, context);
    }

    if (this.addon.isMinimapAllowed()) {
      context.pushStack();
      this.applyZoom(player, context, size, true);
      this.renderEvent.fireWithStage(Stage.ROTATED);
      context.popStack();
    }

    context.pushStack();
    this.applyZoom(player, context, size, false);
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

    MinimapConfiguration configuration = this.configuration();
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
    MinimapBounds bounds = this.renderer.minimapBounds();
    float mapMidX = bounds.getX1() + (bounds.getX2() - bounds.getX1()) / 2F;
    float mapMidZ = bounds.getZ1() + (bounds.getZ2() - bounds.getZ1()) / 2F;

    Position position = player.position();
    Position previousPosition = player.previousPosition();
    double smoothX = position.lerpX(previousPosition, context.getTickDelta());
    double smoothZ = position.lerpZ(previousPosition, context.getTickDelta());

    smoothX -= mapMidX;
    smoothZ -= mapMidZ;

    MinimapConfiguration configuration = this.configuration();
    float pixelLength = size.getActualWidth() / (configuration.zoom().get() * 10F) / 2;
    double offsetX = -pixelLength * smoothX;
    double offsetZ = pixelLength * smoothZ;

    float pixelWidthX = -0.4F;
    float pixelWidthY = -0.4F;

    this.renderEvent.setPixelLength(pixelLength);

    stack.push();
    stack.translate(
        pixelWidthX + offsetX,
        -(pixelWidthY + offsetZ),
        0F
    );

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
    MinimapConfiguration configuration = this.configuration();
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

  private MinimapConfiguration configuration() {
    return this.renderer.configuration();
  }

}
