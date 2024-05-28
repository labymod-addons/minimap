package net.labymod.addons.minimap.map.v2;

import net.labymod.api.Laby;
import net.labymod.api.client.render.matrix.Stack;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.reference.annotation.Referenceable;

@Referenceable
public final class MinimapRenderer {

  private DynamicTexture minimapTexture;
  private DynamicTexture postEffectTexture;

  public MinimapRenderer() {
    this.minimapTexture = new MinimapTexture();
    this.postEffectTexture = new PostEffectTexture();
    Laby.references().eventBus().registerListener(this);
  }

  @Subscribe
  public void onTick(GameTickEvent event) {
    if (event.phase() != Phase.POST) {
      return;
    }

    this.minimapTexture.tick();
    this.postEffectTexture.tick();
  }

  public void render(Stack stack, float x, float y, float width, float height) {
    this.minimapTexture.icon().render(stack, x, y, width, height);
    this.postEffectTexture.icon().render(stack, x, y, width, height);
  }

  public void resize(int newWidth, int newHeight) {
    this.minimapTexture.resize(newWidth, newHeight);
    this.postEffectTexture.resize(newWidth, newHeight);
  }

  public void initialize() {
    this.minimapTexture.initialize();
    this.postEffectTexture.initialize();
  }
}
