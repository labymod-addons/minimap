package net.labymod.addons.minimap.util;

import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.gui.screen.ScreenContext;

public class RenderUtil {

  private static final float HEAD_SIZE = 4.0F;
  private static final float HEAD_OFFSET = HEAD_SIZE / 2.0F;

  public static void renderPlayerHead(ScreenContext context, Player player) {
    context.canvas()
        .submitPlayerFace(
            player.profile(),
            -HEAD_OFFSET, -HEAD_OFFSET,
            HEAD_SIZE, HEAD_SIZE,
            -1,
            true
        );
  }

}
