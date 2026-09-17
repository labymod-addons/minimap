package net.labymod.addons.minimap.worldmap;

import net.labymod.api.client.gui.lss.property.annotation.AutoWidget;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.gui.screen.widget.attributes.bounds.BoundsType;
import net.labymod.api.client.gui.screen.widget.widgets.DivWidget;
import net.labymod.api.util.time.TimeUtil;
import org.jetbrains.annotations.Nullable;

/**
 * On/off indicator styled by the theme through the selected state. The surrounding row handles
 * clicks. The fancy look slides the knob, the vanilla look places it by the theme alone.
 */
@AutoWidget
public final class WorldMapToggleWidget extends DivWidget {

  private static final float KNOB_GAP = 1.0F;
  private static final float ANIMATION_MILLIS = 120.0F;

  private boolean value;
  @Nullable
  private DivWidget knob;
  private float progress = -1.0F;
  private long lastFrameMillis;

  public WorldMapToggleWidget(boolean value) {
    this.value = value;
    this.addId("atlas-toggle");
  }

  @Override
  public void initialize(Parent parent) {
    super.initialize(parent);
    DivWidget knob = new DivWidget();
    knob.addId("atlas-toggle-knob");
    this.addChild(knob);
    this.knob = knob;
    this.setSelected(this.value);
  }

  @Override
  public void renderWidget(ScreenContext context) {
    this.updateKnob();
    super.renderWidget(context);
  }

  boolean value() {
    return this.value;
  }

  void setValue(boolean value) {
    this.value = value;
    this.setSelected(value);
  }

  private void updateKnob() {
    if (this.knob == null) {
      return;
    }

    if (!WorldMapTheme.get().slidesToggleKnob()) {
      this.progress = -1.0F;
      this.knob.setTranslateX(0.0F);
      return;
    }

    long now = TimeUtil.getMillis();
    float target = this.value ? 1.0F : 0.0F;
    if (this.progress < 0.0F) {
      this.progress = target;
    } else {
      float step = (now - this.lastFrameMillis) / ANIMATION_MILLIS;
      this.progress = this.progress < target
          ? Math.min(target, this.progress + step)
          : Math.max(target, this.progress - step);
    }

    this.lastFrameMillis = now;
    float eased = this.progress * this.progress * (3.0F - 2.0F * this.progress);
    float travel = this.bounds().getWidth(BoundsType.INNER)
        - this.knob.bounds().getWidth(BoundsType.OUTER)
        - KNOB_GAP * 2.0F;
    this.knob.setTranslateX(eased * travel);
  }
}
