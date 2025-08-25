package net.labymod.addons.minimap;

import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.laby3d.pipeline.RenderStates;
import net.labymod.laby3d.api.pipeline.RenderState;
import net.labymod.laby3d.api.resource.AssetId;

public final class MinimapRenderStates {

  public static final RenderState GUI_TEXTURED = RenderState.builder(RenderStates.GUI_TEXTURED.toSnippet())
      .setId(AssetId.of(Util.NAMESPACE, "state/gui_textured"))
      .setCull(false)
      .build();

}
