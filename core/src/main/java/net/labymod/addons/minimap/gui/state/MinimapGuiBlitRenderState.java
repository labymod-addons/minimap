package net.labymod.addons.minimap.gui.state;

import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.laby3d.shader.MinimapUniformBlock;
import net.labymod.api.client.gui.screen.state.states.GuiBlitRenderState;
import net.labymod.api.client.gui.screen.state.states.GuiTextureSet;
import net.labymod.api.client.gui.screen.util.scissor.ScissorArea;
import net.labymod.laby3d.api.pipeline.RenderState;
import net.labymod.laby3d.api.pipeline.pass.DrawRenderCommand;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

public class MinimapGuiBlitRenderState extends GuiBlitRenderState {

  private final MinimapUniformBlocks uniformBlocks;

  public MinimapGuiBlitRenderState(
      RenderState renderState,
      Matrix4f pose,
      GuiTextureSet guiTextureSet,
      float x, float y, float width, float height,
      float minU, float minV, float maxU, float maxV,
      int argb,
      @Nullable ScissorArea scissorArea,
      MinimapUniformBlocks uniformBlocks
  ) {
    super(
        renderState,
        pose,
        guiTextureSet,
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
  public void consumeCommand(DrawRenderCommand command) {
    command.setUniformBlock(MinimapUniformBlock.NAME, this.uniformBlocks.minimap());
    super.consumeCommand(command);
  }
}
