package net.labymod.addons.minimap.worldmap;

import java.util.List;
import java.util.function.Consumer;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.gui.screen.activity.Link;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.popup.SimpleAdvancedPopup;

/**
 * Asks for one line of text. Pressing Enter confirms.
 */
@Link("world-map-popup.lss")
public final class WorldMapTextPopup extends SimpleAdvancedPopup {

  private static final int MAX_LENGTH = 32;

  private final TextFieldWidget field = new TextFieldWidget();
  private final Component placeholder;
  private final Consumer<String> submit;

  /**
   * @param submit gets the trimmed text
   */
  WorldMapTextPopup(Component title, Component placeholder, String text, Consumer<String> submit) {
    this.placeholder = placeholder;
    this.submit = submit;
    this.field.setText(text);
    this.title = title;
    this.widgetFunction = this::addWidgets;
    this.buttons = List.of(
        SimplePopupButton.confirm(button -> this.apply()),
        SimplePopupButton.cancel()
    );
  }

  private void addWidgets(VerticalListWidget<Widget> container) {
    this.field.addId("popup-field");
    this.field.maximalLength(MAX_LENGTH);
    this.field.placeholder(this.placeholder);
    this.field.submitHandler(text -> {
      this.apply();
      this.close();
    });
    container.addChild(this.field);
  }

  private void apply() {
    this.submit.accept(this.field.getText().trim());
  }
}
