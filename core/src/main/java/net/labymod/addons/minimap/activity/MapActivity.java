package net.labymod.addons.minimap.activity;

import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.widget.widgets.activity.Document;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;

@Link("map.lss")
public class MapActivity extends SimpleActivity {

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);

    Document document = super.document();
    ButtonWidget buttonWidget = ButtonWidget.component(Component.text("Done"));
    buttonWidget.addId("done");
    buttonWidget.setPressable(this::closeScreen);
    document.addChild(buttonWidget);
  }
}
