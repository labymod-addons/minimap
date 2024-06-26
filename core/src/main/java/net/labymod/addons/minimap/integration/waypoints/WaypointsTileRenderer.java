package net.labymod.addons.minimap.integration.waypoints;

import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.api.renderer.TileRenderer;
import net.labymod.addons.waypoints.WaypointTextures;
import net.labymod.addons.waypoints.Waypoints;
import net.labymod.addons.waypoints.waypoint.Waypoint;
import net.labymod.api.Laby;
import net.labymod.api.client.render.draw.RectangleRenderer;
import net.labymod.api.client.render.font.ComponentRenderMeta;
import net.labymod.api.client.render.font.ComponentRenderer;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.util.Color;
import net.labymod.api.util.math.MathHelper;
import java.util.Collection;

public class WaypointsTileRenderer extends TileRenderer<Waypoint> {

  public WaypointsTileRenderer(MinimapConfigProvider configProvider) {
    super(configProvider);
  }

  @Override
  protected void renderTile(Stack stack, Waypoint waypoint) {
    float distance = this.getCurrentDistance();

    ComponentRenderer componentRenderer = Laby.references().componentRenderer();
    RectangleRenderer rectangleRenderer = Laby.references().rectangleRenderer();

    float maxDistance = 24;
    if (distance < maxDistance) {
      float diff = (maxDistance - distance) / maxDistance;
      int alpha = (int) (255 * diff);

      if (alpha > 3) {

        stack.translate(0F, 0F, 1F);

        ComponentRenderMeta meta = componentRenderer.builder()
            .text(waypoint.title())
            .pos(0, -8F - componentRenderer.height() * 0.33F)
            .centered(true)
            .scale(0.33F)
            .color(Color.withAlpha(0xFFFFFF, alpha))
            .render(stack);

        stack.translate(0F, 0F, -1F);

        rectangleRenderer
            .pos(meta.getLeft() - 0.5F, meta.getTop(), meta.getRight() + 0.5F, meta.getBottom())
            .color(Color.withAlpha(0, MathHelper.clamp(alpha, 0, 100)))
            .render(stack);
      }

    }

    WaypointTextures.MARKER_ICON.render(
        stack,
        -2.25F,
        -8F,
        4.5F,
        8F,
        false,
        waypoint.color().get()
    );


  }

  @Override
  protected float getTileX(Waypoint waypoint) {
    return waypoint.location().getX();
  }

  @Override
  protected float getTileZ(Waypoint waypoint) {
    return waypoint.location().getZ();
  }

  @Override
  protected boolean shouldRender(Stage stage) {
    return stage == Stage.STRAIGHT_ZOOMED;
  }

  @Override
  protected Collection<Waypoint> getTiles() {
    return Waypoints.getReferences().waypointService().getVisibleWaypoints();
  }
}
