package net.labymod.addons.minimap.laby3d;

import net.labymod.addons.minimap.laby3d.shader.MinimapUniformBlock;
import net.labymod.api.Laby;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.laby3d.UniformBlockRegistrationEvent;
import net.labymod.api.laby3d.Laby3D;

public class MinimapUniformBlocks {

  private final Laby3D laby3D;
  private MinimapUniformBlock minimap;

  public MinimapUniformBlocks() {
    this.laby3D = Laby.references().laby3D();
  }

  public MinimapUniformBlock minimap() {
    return this.minimap;
  }

  @Subscribe
  public void onUniformBlockRegistration(UniformBlockRegistrationEvent event) {
    event.registerUniformBlock(
        this.minimap = new MinimapUniformBlock(this.laby3D.renderDevice())
    );
  }
}
