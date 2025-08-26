package net.labymod.addons.minimap.activity;

import net.labymod.addons.minimap.activity.widget.MapWidget;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;

@Link("map.lss")
public class MapActivity extends SimpleActivity {

  private final MinimapRenderer minimapRenderer;

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

    MapWidget mapWidget = new MapWidget(this.minimapRenderer);
    mapWidget.addId("map");
    document.addChild(mapWidget);

    ComponentWidget componentWidget = ComponentWidget.component(Component.text("X: 0 Y: 0 Z: 0"));
    document.addChild(componentWidget);
  }
}
