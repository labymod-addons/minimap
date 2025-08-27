package net.labymod.addons.minimap.map.v2.renderer;

import java.util.Collection;
import net.labymod.addons.minimap.api.config.MinimapConfigProvider;
import net.labymod.addons.minimap.api.renderer.TileRenderer;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.Entity;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.state.TextFlags;
import net.labymod.api.util.Color;

public class EntityTileRenderer extends TileRenderer<Entity> {

  private static final float MAX_DISTANCE = 64.0F * 64.0F;
  private static final float SIZE = 1.0F;
  private static final float OFFSET = SIZE / 2.0F;
  private static final float SCALE = 0.33F;

   public EntityTileRenderer(MinimapConfigProvider configProvider) {
    super(configProvider);
  }

  @Override
  protected boolean shouldRenderTile(Entity entity) {
    if (entity instanceof Player) {
      return false;
    }

    double distanceSquared = this.clientPlayer().getDistanceSquared(entity);
    return !(distanceSquared > MAX_DISTANCE);
  }

  @Override
  protected void renderTile(ScreenContext context, Entity entity) {
    ScreenCanvas canvas = context.canvas();
    canvas.submitCircle(
        0, 0,
        SIZE * 1.25F,
        -1
    );

    context.pushStack();
    context.scale(0.33F, 0.33F);
    canvas.submitComponent(
        entity.nameComponent(),
        0, 1,
        -1,
        Color.withAlpha(0x000000, 100),
        0.33F,
        TextFlags.SHADOW
    );
    context.popStack();

  }

  @Override
  protected float getTileX(Entity entity) {
    return entity.getPosX();
  }

  @Override
  protected float getTileZ(Entity entity) {
    return entity.getPosZ();
  }

  @Override
  protected Collection<Entity> getTiles() {
    return Laby.references().clientWorld().getEntities();
  }
}
