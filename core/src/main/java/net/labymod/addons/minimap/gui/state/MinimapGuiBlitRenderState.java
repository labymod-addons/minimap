package net.labymod.addons.minimap.gui.state;

import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.laby3d.shader.MinimapUniformBlock;
import net.labymod.api.client.gui.screen.state.DrawCommandContext;
import net.labymod.api.client.gui.screen.state.states.GuiBlitRenderState;
import net.labymod.api.client.gui.screen.util.scissor.ScissorArea;
import net.labymod.api.laby3d.pipeline.material.Material;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class MinimapGuiBlitRenderState extends GuiBlitRenderState {

  private final MinimapUniformBlocks uniformBlocks;

  public MinimapGuiBlitRenderState(
      Material material,
      Matrix4f pose,
      float x, float y, float width, float height,
      float minU, float minV, float maxU, float maxV,
      int argb,
      @Nullable ScissorArea scissorArea,
      MinimapUniformBlocks uniformBlocks
  ) {
    super(
        material,
        pose,
        x, y, width, height,
        minU, minV, maxU, maxV,
        argb,
        scissorArea
    );
    this.uniformBlocks = uniformBlocks;
  }

  @Override
  public boolean shouldDirectRecord() {
    return true;
  }

  @Override
  public void consumeCommand(DrawCommandContext command) {
    command.commandBuffer().bindUniformBlock(MinimapUniformBlock.NAME, this.uniformBlocks.minimap());
    super.consumeCommand(command);
  }
}
