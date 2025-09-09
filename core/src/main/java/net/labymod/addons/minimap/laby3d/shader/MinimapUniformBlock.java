package net.labymod.addons.minimap.laby3d.shader;

import java.util.List;
import net.labymod.laby3d.api.RenderDevice;
import net.labymod.laby3d.api.buffers.layout.DataType;
import net.labymod.laby3d.api.buffers.layout.LayoutDefinition;
import net.labymod.laby3d.api.shaders.UniformType;
import net.labymod.laby3d.api.shaders.block.AbstractUniformBlock;
import net.labymod.laby3d.api.shaders.block.property.FloatUniformProperty;
import net.labymod.laby3d.api.shaders.block.property.UniformProperty;
import net.labymod.laby3d.api.shaders.block.property.Vector3fUniformProperty;
import net.labymod.laby3d.api.shaders.block.property.Vector4fUniformProperty;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class MinimapUniformBlock extends AbstractUniformBlock {

  public static final String NAME = "Minimap";

  private static final String PIXEL_SIZE_NAME = "PixelSize";
  private static final String SUN_POSITION_NAME = "SunPosition";
  private static final String DAY_TIME_NAME = "DayTime";
  private static final String COLOR_ADJUSTMENTS_NAME = "ColorAdjustments";

  public static final LayoutDefinition LAYOUT = LayoutDefinition.std140()
      .add(PIXEL_SIZE_NAME, DataType.VEC3, UniformType.VEC3)
      .add(SUN_POSITION_NAME, DataType.VEC3, UniformType.VEC3)
      .add(DAY_TIME_NAME, DataType.SCALAR, UniformType.FLOAT)
      .add(COLOR_ADJUSTMENTS_NAME, DataType.VEC4, UniformType.VEC4)
      .build();

  private final UniformProperty<Vector3f> pixelSize;
  private final UniformProperty<Vector3f> sunPosition;
  private final UniformProperty<Float> dayTime;
  private final UniformProperty<Vector4f> colorAdjustments;

  public MinimapUniformBlock(RenderDevice device) {
    super(device, NAME, LAYOUT);
    this.pixelSize = this.createProperty(PIXEL_SIZE_NAME, Vector3fUniformProperty::new);
    this.sunPosition = this.createProperty(SUN_POSITION_NAME, Vector3fUniformProperty::new);
    this.dayTime = this.createProperty(DAY_TIME_NAME, FloatUniformProperty::new);
    this.colorAdjustments = this.createProperty(COLOR_ADJUSTMENTS_NAME, Vector4fUniformProperty::new);
  }

  public UniformProperty<Vector3f> pixelSize() {
    return this.pixelSize;
  }

  public UniformProperty<Vector3f> sunPosition() {
    return this.sunPosition;
  }

  public UniformProperty<Float> dayTime() {
    return this.dayTime;
  }

  public UniformProperty<Vector4f> colorAdjustments() {
    return this.colorAdjustments;
  }

  @Override
  protected List<UniformProperty<?>> buildProperties() {
    return List.of(this.pixelSize, this.sunPosition, this.dayTime, this.colorAdjustments);
  }
}
