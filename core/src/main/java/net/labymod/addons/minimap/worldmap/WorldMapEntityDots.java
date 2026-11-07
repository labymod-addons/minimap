package net.labymod.addons.minimap.worldmap;

import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.laby3d.MinimapRenderStates;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.entity.Entity;
import net.labymod.api.client.entity.LivingEntity;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.resources.texture.DynamicTexture;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.laby3d.pipeline.material.GuiMaterial;
import net.labymod.api.laby3d.pipeline.material.Material;
import net.labymod.api.util.math.position.Position;
import net.labymod.laby3d.api.textures.SamplerDescription;
import net.labymod.laby3d.api.textures.SamplerDescription.Filter;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a white dot with a dark outline for every living entity that is not a player. All dots
 * share one texture, so they go into a single draw call. Two circles per entity took a draw call
 * each.
 */
final class WorldMapEntityDots {

  // The circles drawn before faded out three quarters of a pixel inside their radius of 1.5 and
  // 2.25. These are the sizes they had on screen
  private static final float RADIUS = 0.75F;
  private static final float OUTLINE_RADIUS = 1.5F;
  private static final float OUTLINE_OPACITY = 0xB0 / 255.0F;
  // Leaves room for the anti-aliased edge, so it is not cut off at the border of the texture
  private static final float EXTENT = OUTLINE_RADIUS + 0.5F;
  private static final SamplerDescription SAMPLER = SamplerDescription.builder()
      .setFilter(Filter.NEAREST)
      .build();

  @Nullable
  private DynamicTexture texture;
  @Nullable
  private Material material;
  private int textureSize;

  /**
   * @param pixelScale physical pixels per screen pixel, the dot texture has one texel per pixel
   */
  void render(
      ScreenCanvas canvas,
      Minecraft minecraft,
      WorldMapCamera camera,
      float width, float height,
      float pixelScale,
      float partialTicks
  ) {
    Material material = this.material(pixelScale);
    float halfTexture = this.textureSize / 2.0F;
    float size = this.textureSize / pixelScale;
    for (Entity entity : minecraft.clientWorld().getEntities()) {
      if (!(entity instanceof LivingEntity) || entity instanceof Player) {
        continue;
      }

      Position position = entity.position();
      Position previous = entity.previousPosition();
      float x = camera.worldToScreenX(position.lerpX(previous, partialTicks), width);
      float y = camera.worldToScreenY(position.lerpZ(previous, partialTicks), height);
      if (x < -EXTENT || x > width + EXTENT || y < -EXTENT || y > height + EXTENT) {
        continue;
      }

      // On whole pixels every texel covers one pixel, so the dot stays as sharp as the circles were
      canvas.submitGuiBlit(
          material,
          Math.round(x * pixelScale - halfTexture) / pixelScale,
          Math.round(y * pixelScale - halfTexture) / pixelScale,
          size, size,
          0.0F, 0.0F, 1.0F, 1.0F,
          -1
      );
    }
  }

  void dispose() {
    if (this.texture != null) {
      this.texture.release();
      this.texture.close();
      this.texture = null;
    }

    this.material = null;
  }

  private Material material(float pixelScale) {
    int textureSize = Math.max(4, (int) Math.ceil(EXTENT * 2.0F * pixelScale));
    if (this.material != null && this.textureSize == textureSize) {
      return this.material;
    }

    this.dispose();
    DynamicTexture texture = new DynamicTexture(
        Util.newDefaultNamespace("texture/worldmap/entity_dot"),
        textureSize, textureSize,
        SAMPLER
    );
    drawDot(texture.getImage(), textureSize);
    texture.upload();

    this.texture = texture;
    this.textureSize = textureSize;
    this.material = GuiMaterial.builder(MinimapRenderStates.GUI_TEXTURED)
        .setTexture(0, texture.deviceTextureView())
        .build();
    return this.material;
  }

  /**
   * The white dot over its outline, with one texel of anti-aliasing at both edges.
   */
  private static void drawDot(GameImage image, int size) {
    float texelsPerPixel = size / (EXTENT * 2.0F);
    float center = size / 2.0F;
    float radius = RADIUS * texelsPerPixel;
    float outlineRadius = OUTLINE_RADIUS * texelsPerPixel;
    for (int y = 0; y < size; y++) {
      for (int x = 0; x < size; x++) {
        float dx = x + 0.5F - center;
        float dy = y + 0.5F - center;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float fill = coverage(radius - distance);
        float outline = coverage(outlineRadius - distance) * OUTLINE_OPACITY;
        float alpha = fill + outline * (1.0F - fill);
        int gray = alpha <= 0.0F ? 0 : Math.round(fill / alpha * 255.0F);
        image.setARGB(x, y, Math.round(alpha * 255.0F) << 24 | gray << 16 | gray << 8 | gray);
      }
    }
  }

  private static float coverage(float edgeDistance) {
    return Math.max(0.0F, Math.min(1.0F, edgeDistance + 0.5F));
  }
}
