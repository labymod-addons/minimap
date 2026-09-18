package net.labymod.addons.minimap.worldmap;

import java.util.List;
import java.util.function.Consumer;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.popup.SimpleAdvancedPopup;

/**
 * Names a sub-world. An empty name goes back to the numbered default.
 */
@Link("world-map-popup.lss")
public final class WorldMapRenamePopup extends SimpleAdvancedPopup {

  private static final int MAX_NAME_LENGTH = 32;

  private final TextFieldWidget name = new TextFieldWidget();
  private final Consumer<String> rename;

  WorldMapRenamePopup(String name, Consumer<String> rename) {
    this.rename = rename;
    this.name.setText(name);
    this.title = Component.translatable(Util.NAMESPACE + ".worldMap.rename.title");
    this.widgetFunction = this::addWidgets;
    this.buttons = List.of(
        SimplePopupButton.confirm(button -> this.apply()),
        SimplePopupButton.cancel()
    );
  }

  private void addWidgets(VerticalListWidget<Widget> container) {
    this.name.addId("world-name");
    this.name.maximalLength(MAX_NAME_LENGTH);
    this.name.placeholder(Component.translatable(Util.NAMESPACE + ".worldMap.rename.placeholder"));
    this.name.submitHandler(text -> {
      this.apply();
      this.close();
    });
    container.addChild(this.name);
  }

  private void apply() {
    this.rename.accept(this.name.getText().trim());
  }
}
