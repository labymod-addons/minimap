package net.labymod.addons.minimap.integration.waypoints;

import java.util.Collection;
import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.api.renderer.TileRenderer;
import net.labymod.addons.minimap.integration.waypoints.WaypointsIntegration.WaypointContainer;
import net.labymod.addons.waypoints.WaypointTextures;
import net.labymod.api.Laby;
import net.labymod.api.client.gfx.pipeline.renderer.text.FormattedTextLayout;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.state.TextFlags;
import net.labymod.api.client.render.matrix.Stack;
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
    Stack stack = context.stack();
    ScreenCanvas canvas = context.canvas();
    float distance = this.getCurrentDistance();

    float maxDistance = 24;
    if (distance < maxDistance) {
      float diff = (maxDistance - distance) / maxDistance;
      int alpha = (int) (255 * diff);

      if (alpha > 3) {

        stack.translate(0F, 0F, 1F);

        float scale =  0.33F;
        stack.scale(scale, scale, 1F);

        canvas.submitComponent(
            waypoint.title(),
            0, -8F - (canvas.getLineHeight() * scale),
            Color.withAlpha(0xFFFFFF, alpha),
            Color.withAlpha(0x000000, MathHelper.clamp(alpha, 0, 100)),
            TextFlags.SHADOW | TextFlags.CENTERED
        );
      }

    }

    canvas.submitIcon(
        WaypointTextures.MARKER_ICON,
        -2.25F,
        -8F,
        4.5F,
        8F,
        false,
        waypoint.color().get()
    );


  }

  @Override
  protected float getTileX(WaypointContainer waypoint) {
    return waypoint.position().getX();
  }

  @Override
  protected float getTileZ(WaypointContainer waypoint) {
    return waypoint.position().getZ();
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
