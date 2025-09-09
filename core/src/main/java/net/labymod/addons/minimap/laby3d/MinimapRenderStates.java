package net.labymod.addons.minimap.laby3d;

import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.laby3d.shader.MinimapUniformBlock;
import net.labymod.api.laby3d.pipeline.RenderStates;
import net.labymod.laby3d.api.pipeline.RenderState;
import net.labymod.laby3d.api.pipeline.shader.ShaderProgramDescription;
import net.labymod.laby3d.api.pipeline.shader.UniformBlockDescription;
import net.labymod.laby3d.api.pipeline.shader.UniformSamplerDescription;
import net.labymod.laby3d.api.resource.AssetId;

public final class MinimapRenderStates {

  public static final RenderState GUI_TEXTURED = RenderState.builder(RenderStates.GUI_TEXTURED.toSnippet())
      .setId(buildStateId("gui_textured"))
      .setCull(false)
      .build();

  public static final RenderState MINIMAP = RenderState.builder(GUI_TEXTURED.toSnippet())
      .setId(buildStateId("minimap"))
      .setShaderProgramDescription(
          ShaderProgramDescription.builder(RenderStates.DEFAULT_SHADER_SNIPPET)
              .setId(buildProgramId("minimap"))
              .setVertexShader(buildShaderId("core/minimap.vsh"))
              .setFragmentShader(buildShaderId("core/minimap.fsh"))
              .addSampler(new UniformSamplerDescription("DiffuseSampler", 0))
              .addSampler(new UniformSamplerDescription("HeightmapSampler", 1))
              .addSampler(new UniformSamplerDescription("LightmapSampler", 2))
              .addUniformBlock(
                  UniformBlockDescription.builder()
                      .setName(MinimapUniformBlock.NAME)
                      .setLayout(MinimapUniformBlock.LAYOUT)
                      .setBinding(11)
                      .build()
              )
              .build()
      )
      .build();

  private static AssetId buildStateId(String path) {
    return AssetId.of(Util.NAMESPACE, "state/" + path);
  }

  private static AssetId buildProgramId(String path) {
    return AssetId.of(Util.NAMESPACE, "program/" + path);
  }

  private static AssetId buildShaderId(String path) {
    return AssetId.of(Util.NAMESPACE, "shaders/" + path);
  }

}
