package net.labymod.addons.minimap.map;

import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.config.MinimapUpdateMethod;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget.MinimapHudWidgetConfig;
import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gfx.texture.GFXTextureFilter;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.client.resources.texture.concurrent.RefreshableTexture;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;

public class MinimapTexture {

  private static final ResourceLocation LOCATION = ResourceLocation.create("minimap", "minimap");
  private static final float ROUND_DECIMALS = 10F;

  private final MinimapHudWidgetConfig config;

  private final MinimapGenerator generator;
  private final Icon icon = Icon.texture(LOCATION);

  private final MinimapBounds currentBounds = new MinimapBounds();

  private RefreshableTexture texture;

  private float animatedHighestBlockY = 0F;

  public MinimapTexture(MinimapHudWidgetConfig config) {
    this.config = config;
    this.generator = new MinimapGenerator();
  }

  public void init() {
    if (this.texture != null) {
      this.texture.release();
    }

    this.texture = Laby.references().asynchronousTextureUploader().newRefreshableTexture(
        GFXTextureFilter.NEAREST,
        GFXTextureFilter.NEAREST
    );
    this.texture.bindTo(LOCATION);

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
  public void tick(GameTickEvent event) {
    if (event.phase() != Phase.POST || this.texture.wasReleased()) {
      return;
    }

    if (!this.config.isEnabled() || !Laby.labyAPI().minecraft().isIngame()) {
      this.generator.reset();
      return;
    }

    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }

    int zoom = this.config.zoom().get() * 10;
    int x1 = (int) (player.getPosX() - zoom);
    int z1 = (int) (player.getPosZ() - zoom);
    int x2 = (int) (player.getPosX() + zoom);
    int z2 = (int) (player.getPosZ() + zoom);
    int depth = (int) player.getPosY();

    MinimapUpdateMethod method = this.config.updateMethod().get();

    if (this.generator.isUpdateNecessary(player, x1, z1, x2, z2, depth, method)) {
      GameImage image = this.generator.getMinimap(
          true,
          x1,
          z1,
          x2,
          z2,
          (int) player.getPosX(),
          (int) player.getPosY(),
          (int) player.getPosZ()
      );

      this.texture.queueUpdate(image)
          .thenAccept(v -> this.currentBounds.update(x1, z1, x2, z2, depth));
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
