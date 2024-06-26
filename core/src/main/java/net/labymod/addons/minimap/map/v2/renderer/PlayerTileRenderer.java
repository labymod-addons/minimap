package net.labymod.addons.minimap.map.v2.renderer;

import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.renderer.TileRenderer;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.render.matrix.Stack;
import java.util.Collection;

public class PlayerTileRenderer extends TileRenderer<Player> {

  private static final float HEAD_SIZE = 4.0F;
  private static final float HEAD_OFFSET = HEAD_SIZE / 2.0F;

  public PlayerTileRenderer(MinimapConfigProvider configProvider) {
    super(configProvider);
  }

  @Override
  protected void renderTile(Stack stack, Player player) {
    Laby.references().renderPipeline()
        .resourceRenderer()
        .head()
        .player(player.profile())
        .pos(-HEAD_OFFSET, -HEAD_OFFSET)
        .size(HEAD_SIZE)
        .render(stack);
  }

  @Override
  protected boolean shouldRenderTile(Player player) {
    return this.clientPlayer() != player;
  }

  @Override
  protected float getTileX(Player player) {
    return player.getPosX();
  }

  @Override
  protected float getTileZ(Player player) {
    return player.getPosZ();
  }

  @Override
  protected Collection<Player> getTiles() {
    return Laby.references().clientWorld().getPlayers();
  }
}
