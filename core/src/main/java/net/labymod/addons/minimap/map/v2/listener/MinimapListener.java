package net.labymod.addons.minimap.map.v2.listener;

import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent;
import net.labymod.addons.minimap.api.event.MinimapRenderEvent.Stage;
import net.labymod.addons.minimap.api.map.MinimapPlayerIcon;
import net.labymod.addons.minimap.util.RenderUtil;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.event.Subscribe;
import net.labymod.api.util.math.MathHelper;

public class MinimapListener {

  private final MinimapConfigProvider configProvider;

  public MinimapListener(MinimapConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  @Subscribe
  public void onMinimapRender(MinimapRenderEvent event) {
    if (event.stage() != Stage.STRAIGHT_ZOOMED) {
      return;
    }

    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }
    float playerX = MathHelper.lerp(player.getPosX(), player.getPreviousPosX());
    float playerZ = MathHelper.lerp(player.getPosZ(), player.getPreviousPosZ());

    Stack stack = event.stack();

    float scale = this.configProvider.widgetConfig().tileSize().get() / 10F;
    float radius = event.size().getActualWidth() / 2F;
    float scaledRadius = radius / event.zoom();


    this.renderClientPlayer(
        stack,
        player,
        playerX, playerZ,
        event.pixelLength(),
        player.getRotationHeadYaw(),
        radius,
        scale, scaledRadius
    );
  }


  private void renderClientPlayer(
      Stack stack,
      Player player,
      float playerX, float playerZ,
      float pixelLength, float rotationHeadYaw,
      float radius,
      float scale, float scaledRadius
  ) {

    float pixelDistanceX = (playerX - playerX - 0.5F) * pixelLength;
    float pixelDistanceZ = (playerZ - playerZ - 0.5F) * pixelLength;

    float cos = MathHelper.cos(MathHelper.toRadiansFloat(-rotationHeadYaw));
    float sin = MathHelper.sin(MathHelper.toRadiansFloat(-rotationHeadYaw));

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

    float size = 2.5F;

    MinimapHudWidgetConfig config = this.configProvider.widgetConfig();
    MinimapPlayerIcon playerIcon = config.playerIcon().get();
    if (playerIcon == MinimapPlayerIcon.PLAYER_HEAD) {
      RenderUtil.renderPlayerHead(stack, player);
    } else {
      playerIcon.render(stack, size, config.playerColor().get());
    }
    stack.pop();

  }

}
