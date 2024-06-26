package net.labymod.addons.minimap.map.v2.listener;

import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.event.Subscribe;
import net.labymod.api.util.math.MathHelper;

public class PlayerRenderer {

  private final MinimapConfigProvider configProvider;

  private float playerX;
  private float playerZ;
  private float scale;
  private float radius;
  private float scaledRadius;

  private float cosHeadRaw;
  private float sinHeadRaw;

  public PlayerRenderer(MinimapConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  @Subscribe
  public void onRender(MinimapRenderEvent event) {
    if (event.stage() != Stage.STRAIGHT_ZOOMED_STENCIL) {
      return;
    }

    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    this.playerX = MathHelper.lerp(player.getPosX(), player.getPreviousPosX());
    this.playerZ = MathHelper.lerp(player.getPosZ(), player.getPreviousPosZ());

    this.scale = this.configProvider.widgetConfig().tileSize().get() / 10.0F;
    this.radius = event.size().getActualWidth() / 2.0F;

    this.scaledRadius = this.radius / event.zoom();

    this.cosHeadRaw = MathHelper.cos(MathHelper.toRadiansFloat(-player.getRotationHeadYaw()));
    this.sinHeadRaw = MathHelper.sin(MathHelper.toRadiansFloat(-player.getRotationHeadYaw()));

    this.render(event.stack(), event.pixelLength());
  }

  public void render(Stack stack, float pixelLength) {

    for (Player player : Laby.references().clientWorld().getPlayers()) {
      if (player == Laby.labyAPI().minecraft().getClientPlayer()) {
        continue;
      }

      float pixelDistanceX = (this.playerX - player.getPosX()) * pixelLength;
      float pixelDistanceZ = (this.playerZ - player.getPosZ()) * pixelLength;

      float rotX = this.cosHeadRaw * pixelDistanceX - this.sinHeadRaw * pixelDistanceZ;
      float rotZ = this.sinHeadRaw * pixelDistanceX + this.cosHeadRaw * pixelDistanceZ;

      if (rotX < -this.scaledRadius) {
        rotX = -this.scaledRadius;
      }
      if (rotZ < -this.scaledRadius) {
        rotZ = -this.scaledRadius;
      }

      if (rotX > this.scaledRadius) {
        rotX = this.scaledRadius;
      }
      if (rotZ > this.scaledRadius) {
        rotZ = this.scaledRadius;
      }

      stack.push();

      stack.translate(rotX + this.radius, rotZ + this.radius, 0F);
      stack.scale(this.scale, this.scale, 1F);

      float headSize = 4;

      Laby.references().renderPipeline()
          .resourceRenderer()
          .head()
          .player(player.profile())
          .pos(-(headSize / 2), -(headSize / 2))
          .size(headSize)
          .render(stack);

      stack.pop();
    }

  }

}
