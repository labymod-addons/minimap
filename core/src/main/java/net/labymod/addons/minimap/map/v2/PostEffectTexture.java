package net.labymod.addons.minimap.map.v2;

import net.labymod.api.Laby;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.world.effect.PotionEffect;
import net.labymod.api.util.color.format.ColorFormat;

public class PostEffectTexture extends DynamicTexture {

  private static final String BLINDNESS_ID = "blindness";

  private boolean changed;
  private PotionEffect blindnessPotionEffect;

  public PostEffectTexture() {
    super("minimap/post_effect");
  }

  @Override
  public void tick() {
    boolean hasBlindness = this.blindnessPotionEffect != null;
    int duration = hasBlindness ? this.blindnessPotionEffect.getDuration() : 0;
    if (hasBlindness && duration <= 20) {
      this.changed = true;
    }

    if (this.changed) {
      if (hasBlindness) {
        int width = this.getWidth();
        int height = this.getHeight();

        int centerX = width / 2;
        int centerY = height / 2;

        float radius = duration > 20 ? 30f : (20 - duration);
        float smoothing = radius - 1.0F;

        if (smoothing < 0.0F) {
          smoothing = 0.0F;
        }

        float fullRadius = radius + smoothing;

        for (int y = 0; y < width; y++) {
          for (int x = 0; x < height; x++) {

            float distX = centerX - x;
            float distY = centerY - y;
            float distSquared = (float) Math.sqrt(distX * distX + distY * distY);

            if (distSquared > fullRadius) {
              this.image().setARGB(x, y, 0xFF000000);
            } else if (distSquared > radius) {

              float distanceFromEdge = distSquared - radius;
              float proportionInside = (Math.min(distanceFromEdge, smoothing) / smoothing);

              ColorFormat format = ColorFormat.ARGB32;
              int packedArgb = format.pack(0, 0, 0, proportionInside);
              this.image().setARGB(x, y, packedArgb);
            }
          }
        }
      } else {
        this.image().fillRect(0, 0, this.image().getWidth(), this.image().getHeight(), 0x00FFFFFF);
      }

      this.updateTexture();
      this.changed = false;
    }

    this.checkForBlindness();
  }

  private void checkForBlindness() {
    ClientPlayer clientPlayer = Laby.labyAPI().minecraft().getClientPlayer();
    if (clientPlayer == null) {
      return;
    }

    // TODO (Christian) Add a potion event
    PotionEffect potionEffect = null;
    for (PotionEffect effect : clientPlayer.getActivePotionEffects()) {
      if (effect.getTranslationKey().contains(BLINDNESS_ID)) {
        potionEffect = effect;
        break;
      }
    }

    this.setBlindnessPotionEffect(potionEffect);
  }

  public void setBlindnessPotionEffect(PotionEffect effect) {
    boolean changed = this.blindnessPotionEffect != effect;
    this.blindnessPotionEffect = effect;
    if (changed) {
      this.changed = true;
    }
  }
}
