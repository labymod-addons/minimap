package net.labymod.addons.minimap.activity.widget;

import net.labymod.addons.minimap.api.map.MinimapBounds;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.mouse.Mouse;
import net.labymod.api.client.gui.mouse.MutableMouse;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.state.ScreenCanvas;
import net.labymod.api.client.gui.screen.widget.SimpleWidget;
import net.labymod.api.client.gui.screen.widget.attributes.bounds.Bounds;
import net.labymod.api.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

@AutoWidget
public class MapWidget extends SimpleWidget {

  private final MinimapRenderer renderer;
  private int zoom = 10;

  public MapWidget(MinimapRenderer renderer) {
    this.renderer = renderer;
  }

  @Override
  public void renderWidget(ScreenContext context) {
    super.renderWidget(context);
    this.renderer.setZoomSupplier(() -> this.zoom);

    this.renderer.renderMinimap(
        () -> true,
        () -> {
          Bounds bounds = this.bounds();

          // Widget area in screen pixels
          float mapX = bounds.getX();
          float mapY = bounds.getY();
          float mapW = Math.max(bounds.getWidth(), 1f);
          float mapH = Math.max(bounds.getHeight(), 1f);

          MinimapBounds world = this.renderer.minimapBounds();
          int worldSpanX = Math.max(world.getX2() - world.getX1(), 1);
          int worldSpanZ = Math.max(world.getZ2() - world.getZ1(), 1);

          float texelW = Math.max(1f, (float) Math.floor(mapW / worldSpanX));
          float texelH = Math.max(1f, (float) Math.floor(mapH / worldSpanZ));

          this.renderer.render(
              context,
              bounds.getX(), bounds.getY(),
              bounds.getWidth(), bounds.getHeight()
          );

          MutableMouse mouse = context.mouse();

          ScreenCanvas canvas = context.canvas();

          // Snap the mouse to the texel grid
          int gridX = (int) Math.floor((mouse.getX() - mapX) / texelW);
          int gridY = (int) Math.floor((mouse.getY() - mapY) / texelH);

          // Convert back to screen position, locked to texel grid
          float snappedX = mapX + gridX * texelW;
          float snappedY = mapY + gridY * texelH;

          canvas.submitRelativeRect(
              snappedX, snappedY,
              texelW, texelH,
              0x80FFFFFF
          );
        }
    );
  }

  public @Nullable Vector2f resolveBlockCoordinates(Mouse mouse) {
    Bounds bounds = this.bounds();

    float mapX = bounds.getX();
    float mapY = bounds.getY();
    float mapW = bounds.getWidth();
    float mapH = bounds.getHeight();

    float mx = mouse.getX();
    float my = mouse.getY();

    if (mx >= mapX && mx <= mapX + mapW && my >= mapY && my <= mapY + mapH) {
      float u = (mx - mapX) / mapW;
      float v = (my - mapY) / mapH;

      MinimapBounds world = this.renderer.minimapBounds();

      int minX = world.getX1();
      int minZ = world.getZ1();
      int maxX = world.getX2();
      int maxZ = world.getZ2();

      u = MathHelper.clamp(u, 0.0F, 1.0F);
      v = MathHelper.clamp(v, 0.0F, 1.0F);

      int worldX = minX + (int) Math.floor(u * (maxX - minX));
      int worldZ = minZ + (int) Math.floor(v * (maxZ - minZ));

      return new Vector2f(worldX, worldZ);
    }

    return null;
  }

  @Override
  public void tick() {
    super.tick();
    this.renderer.tick();
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
  }

  @Override
  public boolean mouseScrolled(MutableMouse mouse, double scrollDelta) {
    if (scrollDelta > 0) {
      this.zoom++;
    } else {
      this.zoom--;

    }
    this.zoom = MathHelper.clamp(this.zoom, 2, 32);
    return super.mouseScrolled(mouse, scrollDelta);
  }
}
