package net.labymod.addons.minimap.map;

import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.api.MinimapHudWidgetConfig;
import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.api.map.MinimapUpdateMethod;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.texture.TextureHandle.FilterMode;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.DynamicTexture;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.render.GameRenderEvent;
import net.labymod.api.util.math.position.Position;

public class MinimapTexture {

  private static final ResourceLocation LOCATION = ResourceLocation.create("minimap", "minimap");
  private static final float ROUND_DECIMALS = 10F;

  private final MinimapAddon addon;
  private final MinimapHudWidgetConfig config;

  private final MinimapGenerator generator;
  private final Icon icon = Icon.texture(LOCATION);

  private final MinimapBounds currentBounds = new MinimapBounds();

  private DynamicTexture texture;

  private float animatedHighestBlockY = 0F;

  public MinimapTexture(MinimapAddon addon, MinimapHudWidgetConfig config) {
    this.addon = addon;
    this.config = config;
    this.generator = new MinimapGenerator();
  }

  public void init() {
    if (this.texture != null) {
      this.texture.release();
    }

    this.texture = new DynamicTexture(LOCATION, GameImage.IMAGE_PROVIDER.createImage(16, 16));
    this.texture.setFilter(FilterMode.NEAREST, false);
    this.texture.upload();

    Laby.references().textureRepository().register(LOCATION, this.texture);

    Laby.labyAPI().eventBus().registerListener(this);
  }

  public void release() {
    if (this.texture != null) {
      this.texture.release();
    }

    this.generator.reset();

    Laby.labyAPI().eventBus().unregisterListener(this);
  }

  @Subscribe
  public void tick(GameRenderEvent event) {
    if (event.phase() != Phase.POST) {
      return;
    }

    if (!this.config.isEnabled()
        || !Laby.labyAPI().minecraft().isIngame()
        || !this.addon.isMinimapAllowed()) {
      this.generator.reset();
      return;
    }

    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    Position position = player.position();

    int zoom = this.config.zoom().get() * 10;

    double playerX = position.getX();
    double playerY = position.getY();
    double playerZ = position.getZ();

    int x1 = (int) (playerX - zoom);
    int z1 = (int) (playerZ - zoom);
    int x2 = (int) (playerX + zoom);
    int z2 = (int) (playerZ + zoom);
    int depth = (int) playerY;

    MinimapUpdateMethod method = this.config.updateMethod()
        .getOrDefault(MinimapUpdateMethod.CHUNK_TRIGGER);

    if (this.generator.isUpdateNecessary(player, x1, z1, x2, z2, depth, method)) {
      GameImage image = this.generator.getMinimap(
          true,
          x1,
          z1,
          x2,
          z2,
          (int) playerX,
          (int) playerY,
          (int) playerZ
      );

      this.texture.setImageAndUpload(image);
      Laby.labyAPI().minecraft()
          .executeNextTick(() -> this.currentBounds.update(x1, z1, x2, z2, depth));
    }

    float highestBlockY = (this.generator.getHighestBlockY() / ROUND_DECIMALS) * ROUND_DECIMALS;

    if (this.animatedHighestBlockY < highestBlockY) {
      this.animatedHighestBlockY += 0.5F;
    }
    if (this.animatedHighestBlockY > highestBlockY) {
      this.animatedHighestBlockY -= 0.5F;
    }
  }

  public ResourceLocation location() {
    return LOCATION;
  }

  public Icon icon() {
    return this.icon;
  }

  public float getAnimatedHighestBlockY() {
    return this.animatedHighestBlockY;
  }

  public boolean isAvailable() {
    return this.generator.isImageAvailable();
  }

  public MinimapBounds getCurrentBounds() {
    return this.currentBounds;
  }
}
