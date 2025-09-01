package net.labymod.addons.minimap.map.v2.renderer;

import java.util.Collection;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.config.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.map.MinimapPlayerIcon;
import net.labymod.addons.minimap.api.renderer.TileRenderer;
import net.labymod.addons.minimap.util.RenderUtil;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.gui.screen.ScreenContext;

public class PlayerTileRenderer extends TileRenderer<Player> {

  public PlayerTileRenderer(MinimapConfigProvider configProvider) {
    super(configProvider);
  }

  @Override
  protected void renderTile(ScreenContext context, Player player) {
    if (this.clientPlayer() == player) {
      MinimapHudWidgetConfig config = this.configProvider().hudWidgetConfig();
      MinimapPlayerIcon playerIcon = config.playerIcon().get();
      if (playerIcon == MinimapPlayerIcon.PLAYER_HEAD) {
        RenderUtil.renderPlayerHead(context, player);
      } else {
        playerIcon.render(context, 2.5F, config.playerColor().get());
      }
    } else {
      RenderUtil.renderPlayerHead(context, player);
    }
  }

  @Override
  protected boolean shouldRenderTile(Player player) {
    if (player == this.clientPlayer()) {
      return this.configProvider().hudWidgetConfig().showOwnPlayer().get();
    } else {
      return this.configProvider().hudWidgetConfig().showPlayers().get();
    }
  }

  @Override
  protected float getTileX(Player player) {
    return (float) player.position().getX();
  }

  @Override
  protected float getTileZ(Player player) {
    return (float) player.position().getZ();
  }

  @Override
  protected Collection<Player> getTiles() {
    return Laby.references().clientWorld().getPlayers();
  }
}
