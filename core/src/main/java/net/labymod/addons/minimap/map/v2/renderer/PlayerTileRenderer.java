package net.labymod.addons.minimap.map.v2.renderer;

import java.util.Collection;
import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.renderer.TileRenderer;
import net.labymod.addons.minimap.util.RenderUtil;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.render.matrix.Stack;

public class PlayerTileRenderer extends TileRenderer<Player> {

  public PlayerTileRenderer(MinimapConfigProvider configProvider) {
    super(configProvider);
  }

  @Override
  protected void renderTile(Stack stack, Player player) {
    RenderUtil.renderPlayerHead(stack, player);
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
