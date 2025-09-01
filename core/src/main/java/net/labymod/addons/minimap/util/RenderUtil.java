package net.labymod.addons.minimap.util;

import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;

public class RenderUtil {

  private static final float HEAD_SIZE = 4.0F;
  private static final float HEAD_OFFSET = HEAD_SIZE / 2.0F;

  public static void renderPlayerHead(ScreenContext context, Player player) {
    ScreenCanvas canvas = context.canvas();
    context.pushStack();
    context.translate(Util.SHADOW_OFFSET, Util.SHADOW_OFFSET, 0.0F);
    canvas.submitPlayerFace(
        player.profile(),
        -HEAD_OFFSET, -HEAD_OFFSET,
        HEAD_SIZE, HEAD_SIZE,
        Util.SHADOW_COLOR,
        true
    );
    context.popStack();

    canvas.submitPlayerFace(
        player.profile(),
        -HEAD_OFFSET, -HEAD_OFFSET,
        HEAD_SIZE, HEAD_SIZE,
        -1,
        true
    );

  }

}
