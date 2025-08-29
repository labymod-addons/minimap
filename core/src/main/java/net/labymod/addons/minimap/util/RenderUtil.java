package net.labymod.addons.minimap.util;

import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.util.color.format.ColorFormat;

public class RenderUtil {

  public static final float SHADOW_OFFSET = 0.5F;
  public static final float SHADOW_SCALE = 0.25F;
  public static final int SHADOW_COLOR = ColorFormat.ARGB32.mul(
      0xFF000000,
      SHADOW_SCALE,
      SHADOW_SCALE,
      SHADOW_SCALE,
      1.0F
  );

  private static final float HEAD_SIZE = 4.0F;
  private static final float HEAD_OFFSET = HEAD_SIZE / 2.0F;

  public static void renderPlayerHead(ScreenContext context, Player player) {
    ScreenCanvas canvas = context.canvas();
    context.pushStack();
    context.translate(SHADOW_OFFSET, SHADOW_OFFSET, 0.0F);
    canvas.submitPlayerFace(
        player.profile(),
        -HEAD_OFFSET, -HEAD_OFFSET,
        HEAD_SIZE, HEAD_SIZE,
        SHADOW_COLOR,
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
