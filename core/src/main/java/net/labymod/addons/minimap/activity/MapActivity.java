package net.labymod.addons.minimap.activity;

import java.util.Objects;
import net.labymod.addons.minimap.activity.widget.MapWidget;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import org.joml.Vector2f;

@Link("map.lss")
public class MapActivity extends SimpleActivity {

  private final MinimapRenderer minimapRenderer;
  private MapWidget mapWidget;
  private ComponentWidget coordinatesWidget;

  private Vector2f previousBlockCoordinates;

  public MapActivity(MinimapRenderer minimapRenderer) {
    this.minimapRenderer = minimapRenderer;
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    Document document = super.document();
    ButtonWidget buttonWidget = ButtonWidget.component(Component.text("Done"));
    buttonWidget.addId("done");
    buttonWidget.setPressable(this::closeScreen);
    document.addChild(buttonWidget);

    this.mapWidget = new MapWidget(this.minimapRenderer);
    this.mapWidget.addId("map");
    document.addChild(this.mapWidget);

    this.coordinatesWidget = ComponentWidget.component(Component.empty());
    document.addChild(this.coordinatesWidget);
  }

  @Override
  public void render(ScreenContext context) {
    super.render(context);

    Vector2f blockCoordinates = this.mapWidget.resolveBlockCoordinates(context.mouse());
    if (!Objects.equals(blockCoordinates, this.previousBlockCoordinates)) {
      this.previousBlockCoordinates = blockCoordinates;
      this.coordinatesWidget.setComponent(this.buildCoordinatesComponent(blockCoordinates));
    }
  }

  private Component buildCoordinatesComponent(Vector2f blockCoordinates) {
    if (blockCoordinates == null) {
      return Component.empty();
    }

    return Component.text("X: " + blockCoordinates.x() + " Z: " + blockCoordinates.y());
  }
}
