package net.labymod.addons.minimap.activity.widget;

import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.widget.SimpleWidget;
import net.labymod.api.client.gui.screen.widget.attributes.bounds.Bounds;

@AutoWidget
public class MapWidget extends SimpleWidget {

  private final MinimapRenderer renderer;

  public MapWidget(MinimapRenderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public void renderWidget(ScreenContext context) {
    super.renderWidget(context);

    this.renderer.renderMinimap(() -> true, () -> {
      Bounds bounds = this.bounds();
      this.renderer.render(
          context,
          bounds.getX(), bounds.getY(),
          bounds.getWidth(), bounds.getHeight()
      );
    });
  }

  @Override
  public void tick() {
    super.tick();
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    this.renderer.initialize();
  }
}
