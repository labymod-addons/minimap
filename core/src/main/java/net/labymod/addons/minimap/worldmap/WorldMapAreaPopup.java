package net.labymod.addons.minimap.worldmap;

import java.util.List;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.world.MapArea;
import net.labymod.addons.minimap.world.MapAreaStore;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.color.ColorPickerWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.popup.SimpleAdvancedPopup;
import net.labymod.api.util.Color;

/**
 * Edits the name and color of a marked area.
 */
@Link("world-map-area-popup.lss")
public final class WorldMapAreaPopup extends SimpleAdvancedPopup {

  static final int MAX_NAME_LENGTH = 32;

  private final MapArea area;
  private final MapAreaStore store;
  private final TextFieldWidget name = new TextFieldWidget();
  private final ColorPickerWidget color;

  WorldMapAreaPopup(MapArea area, MapAreaStore store) {
    this.area = area;
    this.store = store;
    this.title = Component.translatable(Util.NAMESPACE + ".worldMap.area.editTitle");
    this.color = ColorPickerWidget.of(Color.of(area.color(), 255))
        .customColors(WorldMapAreaEditor.paletteColors());
    this.widgetFunction = this::addWidgets;
    this.buttons = List.of(
        SimplePopupButton.confirm(button -> this.apply()),
        SimplePopupButton.cancel()
    );
  }

  private void addWidgets(VerticalListWidget<Widget> container) {
    this.name.addId("area-name");
    this.name.maximalLength(MAX_NAME_LENGTH);
    this.name.setText(this.area.name());
    this.name.submitHandler(text -> {
      this.apply();
      this.close();
    });
    container.addChild(this.name);

    this.color.addId("area-color");
    container.addChild(this.color);
  }

  private void apply() {
    this.area.setName(this.name.getText().trim());
    this.area.setColor(this.color.value().get());
    this.store.save();
  }
}
