package net.labymod.addons.minimap.integration.waypoints;

import java.util.Collection;
import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.waypoints.WaypointTextures;
import net.labymod.addons.waypoints.Waypoints;
import net.labymod.addons.waypoints.waypoint.Waypoint;
import net.labymod.api.Laby;
import net.labymod.api.addon.integration.AddonIntegration;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.render.RenderPipeline;
import net.labymod.api.client.render.font.ComponentRenderMeta;
import net.labymod.api.client.render.font.ComponentRenderer;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.event.Subscribe;
import net.labymod.api.util.Color;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.vector.FloatVector3;

public class WaypointsIntegration implements AddonIntegration {

  @Override
  public void load() {
  }

  @Override
  public void onIntegratedAddonEnable() {
    Laby.labyAPI().eventBus().registerListener(this);
  }

  @Override
  public void onIntegratedAddonDisable() {
    Laby.labyAPI().eventBus().unregisterListener(this);
  }

  @Subscribe
  public void renderWaypoints(MinimapRenderEvent event) {
    if (event.stage() != Stage.STRAIGHT_ZOOMED) {
      return;
    }

    Collection<Waypoint> waypoints = Waypoints.getReferences().waypointService().getVisibleWaypoints();
    if (waypoints.isEmpty()) {
      return;
    }

    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }
    float playerX = MathHelper.lerp(player.getPosX(), player.getPreviousPosX());
    float playerZ = MathHelper.lerp(player.getPosZ(), player.getPreviousPosZ());

    RenderPipeline pipeline = Laby.labyAPI().renderPipeline();
    ComponentRenderer componentRenderer = pipeline.componentRenderer();
    Stack stack = event.stack();

    float scale = MinimapAddon.getReferences().minimapConfigProvider().widgetConfig()
        .tileSize().get() / 10F;
    float radius = event.size().getActualWidth() / 2F;
    float scaledRadius = radius / event.zoom();

    for (Waypoint waypoint : waypoints) {
      FloatVector3 pos = waypoint.location();

      float pixelDistanceX = (playerX - pos.getX() - 0.5F) * event.pixelLength();
      float pixelDistanceZ = (playerZ - pos.getZ() - 0.5F) * event.pixelLength();

      float cos = MathHelper.cos(MathHelper.toRadiansFloat(-player.getRotationHeadYaw()));
      float sin = MathHelper.sin(MathHelper.toRadiansFloat(-player.getRotationHeadYaw()));

      float rotX = cos * pixelDistanceX - sin * pixelDistanceZ;
      float rotZ = sin * pixelDistanceX + cos * pixelDistanceZ;

      if (rotX < -scaledRadius) {
        rotX = -scaledRadius;
      }
      if (rotZ < -scaledRadius) {
        rotZ = -scaledRadius;
      }

      if (rotX > scaledRadius) {
        rotX = scaledRadius;
      }
      if (rotZ > scaledRadius) {
        rotZ = scaledRadius;
      }

      stack.push();

      stack.translate(rotX + radius, rotZ + radius, 0F);
      stack.scale(scale, scale, 1F);

      stack.translate(0F, 0F, 1F);
      ComponentRenderMeta meta = componentRenderer.builder()
          .text(waypoint.title())
          .pos(0, -8F - componentRenderer.height() * 0.33F)
          .centered(true)
          .scale(0.33F)
          .render(stack);
      stack.translate(0F, 0F, -1F);

      pipeline.rectangleRenderer()
          .pos(meta.getLeft() - 0.5F, meta.getTop(), meta.getRight() + 0.5F, meta.getBottom())
          .color(Color.withAlpha(0, 100))
          .render(stack);

      WaypointTextures.MARKER_ICON.render(
          stack,
          -2.25F,
          -8F,
          4.5F,
          8F,
          false,
          waypoint.color().get()
      );

      stack.pop();
    }
  }
}
