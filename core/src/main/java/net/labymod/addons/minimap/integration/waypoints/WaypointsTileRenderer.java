package net.labymod.addons.minimap.integration.waypoints;

import java.util.Collection;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.api.renderer.TileRenderer;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.integration.waypoints.WaypointsIntegration.WaypointContainer;
import net.labymod.api.Laby;
import net.labymod.api.client.gfx.pipeline.renderer.text.FormattedTextLayout;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.state.TextFlags;
import net.labymod.api.util.Color;
import net.labymod.api.util.math.MathHelper;

public class WaypointsTileRenderer extends TileRenderer<WaypointContainer> {

  private final WaypointsIntegration integration;
  private final FormattedTextLayout.Factory factory;

  public WaypointsTileRenderer(
      WaypointsIntegration integration,
      MinimapConfigProvider configProvider
  ) {
    super(configProvider);
    this.integration = integration;
    this.factory = Laby.references().formattedTextLayoutFactory();
  }

  @Override
  protected void renderTile(ScreenContext context, WaypointContainer waypoint) {
    ScreenCanvas canvas = context.canvas();
    float distance = this.getCurrentDistance();

    float maxDistance = 24;
    if (distance < maxDistance) {
      float diff = (maxDistance - distance) / maxDistance;
      int alpha = (int) (255 * diff);

      if (alpha > 3) {
        float scale = 0.33F;
        canvas.submitComponent(
            waypoint.title(),
            0, -8F - (canvas.getLineHeight() * scale),
            Color.withAlpha(0xFFFFFF, alpha),
            Color.withAlpha(0x000000, MathHelper.clamp(alpha, 0, 100)),
            scale,
            TextFlags.SHADOW | TextFlags.CENTERED
        );
      }

    }

    Icon icon = waypoint.icon();
    context.pushStack();
    context.translate(Util.SHADOW_OFFSET, Util.SHADOW_OFFSET, 0.0F);

    int iconColor = waypoint.iconColor();
    canvas.submitIcon(
        icon,
        -6.5F / 2.0F,
        -16 / 2.0F,
        16.0F / 2.0F,
        16.0F / 2.0F,
        false,
        Util.applyShadowColor(iconColor)
    );

    context.popStack();

    canvas.submitIcon(
        icon,
        -6.5F / 2.0F,
        -16 / 2.0F,
        16.0F / 2.0F,
        16.0F / 2.0F,
        false,
        iconColor
    );
  }

  @Override
  public boolean isEnabled() {
    return this.configProvider().hudWidgetConfig().showWaypoints().get();
  }

  @Override
  protected float getTileX(WaypointContainer waypoint) {
    return (float) waypoint.position().getX();
  }

  @Override
  protected float getTileZ(WaypointContainer waypoint) {
    return (float) waypoint.position().getZ();
  }

  @Override
  protected boolean shouldRender(Stage stage) {
    return stage == Stage.STRAIGHT_ZOOMED;
  }

  @Override
  protected Collection<WaypointContainer> getTiles() {
    return this.integration.getWaypoints();
  }
}
