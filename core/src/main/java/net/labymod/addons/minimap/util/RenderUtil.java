package net.labymod.addons.minimap.util;

import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.generated.ReferenceStorage;

public class RenderUtil {

  private static final float HEAD_SIZE = 4.0F;
  private static final float HEAD_OFFSET = HEAD_SIZE / 2.0F;

  public static void renderPlayerHead(Stack stack, Player player) {
    ReferenceStorage references = Laby.references();

    references.resourceRenderer()
        .head()
        .player(player.profile())
        .pos(-HEAD_OFFSET, -HEAD_OFFSET)
        .size(HEAD_SIZE)
        .render(stack);
  }

}
