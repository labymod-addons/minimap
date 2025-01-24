package net.labymod.addons.minimap.debug;

import net.labymod.api.Laby;
import net.labymod.api.client.gfx.imgui.ImGuiAccessor;
import net.labymod.api.client.gfx.imgui.ImGuiWindow;
import net.labymod.api.client.gfx.imgui.LabyImGui;
import net.labymod.api.client.gfx.imgui.type.ImGuiBooleanType;
import org.jetbrains.annotations.Nullable;

public class ImGuiMinimapDebug extends ImGuiWindow {

  public ImGuiMinimapDebug(@Nullable ImGuiBooleanType visible) {
    super("Minimap Debug", visible, 0);
  }

  @Override
  protected void renderContent() {
    LabyImGui.beginGroup();
    this.renderImage("Color", MinimapDebugger.COLOR_MAP_TEXTURE);
    this.renderImage("Heightmap", MinimapDebugger.HEIGHTMAP_TEXTURE);
    this.renderImage("Lightmap", MinimapDebugger.LIGHTMAP_TEXTURE);
    LabyImGui.endGroup();
  }

  private void renderImage(String title, MinimapDebugger.TextureInfo info) {
  //  LabyImGui.text(title);
    if (info.getId() == MinimapDebugger.INVALID_TEXTURE_ID) {
      LabyImGui.text("No texture was set");
    } else {
      ImGuiAccessor accessor = Laby.references().imGuiAccessor();
      int width = Math.max(255, info.getWidth());
      int height = Math.max(255, info.getHeight());

      width = 900;
      height = 900;

      accessor.image(info.getId(), width, height);
      LabyImGui.sameLine();
    }
  }
}
