package net.labymod.addons.minimap.map.v2.renderer;

import net.labymod.addons.minimap.api.MinimapConfigProvider;
import net.labymod.addons.minimap.api.renderer.TileRenderer;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.Entity;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.render.matrix.Stack;
import java.util.Collection;

public class EntityTileRenderer extends TileRenderer<Entity> {

  private static final float SIZE = 1.0F;
  private static final float OFFSET = SIZE / 2.0F;

  public EntityTileRenderer(MinimapConfigProvider configProvider) {
    super(configProvider);
  }

  @Override
  protected boolean shouldRenderTile(Entity entity) {
    return !(entity instanceof Player);
  }

  @Override
  protected void renderTile(Stack stack, Entity entity) {
    Laby.references().rectangleRenderer()
        .pos(-OFFSET, -OFFSET)
        .size(SIZE)
        .color(-1)
        .render(stack);

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
