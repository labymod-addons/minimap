package net.labymod.addons.minimap.world;

import java.util.UUID;

/**
 * A marked rectangle or circle on the map. Both shapes are stored as block edges, the maximum
 * edges are exclusive. A circle fits its square bounds and covers whole blocks.
 */
public final class MapArea {

  private final String id;
  private final Shape shape;
  private String name;
  private int color;
  private boolean visible;
  private int minX;
  private int minZ;
  private int maxX;
  private int maxZ;

  public MapArea(Shape shape, String name, int color, int minX, int minZ, int maxX, int maxZ) {
    this.id = UUID.randomUUID().toString();
    this.shape = shape;
    this.name = name;
    this.color = color;
    this.visible = true;
    this.setBounds(minX, minZ, maxX, maxZ);
  }

  public String id() {
    return this.id;
  }

  public Shape shape() {
    return this.shape;
  }

  public String name() {
    return this.name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * @return the color in RGB, without alpha
   */
  public int color() {
    return this.color;
  }

  public void setColor(int color) {
    this.color = color & 0xFFFFFF;
  }

  public boolean isVisible() {
    return this.visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public int minX() {
    return this.minX;
  }

  public int minZ() {
    return this.minZ;
  }

  public int maxX() {
    return this.maxX;
  }

  public int maxZ() {
    return this.maxZ;
  }

  public double centerX() {
    return (this.minX + this.maxX) / 2.0D;
  }

  public double centerZ() {
    return (this.minZ + this.maxZ) / 2.0D;
  }

  /**
   * Orders the edges and keeps the area at least one block large.
   */
  public void setBounds(int x1, int z1, int x2, int z2) {
    this.minX = Math.min(x1, x2);
    this.minZ = Math.min(z1, z2);
    this.maxX = Math.max(Math.max(x1, x2), this.minX + 1);
    this.maxZ = Math.max(Math.max(z1, z2), this.minZ + 1);
  }

  public boolean contains(double x, double z) {
    if (x < this.minX || x >= this.maxX || z < this.minZ || z >= this.maxZ) {
      return false;
    }

    if (this.shape == Shape.RECTANGLE) {
      return true;
    }

    int blockX = (int) Math.floor(x);
    int inset = this.circleInset((int) Math.floor(z));
    return blockX >= this.minX + inset && blockX < this.maxX - inset;
  }

  /**
   * A circle covers every block whose middle lies within its radius.
   *
   * @return the number of blocks cut from each side of the row. An empty row returns at least half
   * the width.
   */
  public int circleInset(int z) {
    int width = this.maxX - this.minX;
    int empty = (width + 1) / 2;
    if (z < this.minZ || z >= this.maxZ) {
      return empty;
    }

    double radius = width / 2.0D;
    double deltaZ = z + 0.5D - this.centerZ();
    double halfSquared = radius * radius - deltaZ * deltaZ;
    if (halfSquared < 0.0D) {
      return empty;
    }

    // The epsilon counts a block middle exactly on the radius as inside
    int inset = (int) Math.ceil(radius - 0.5D - Math.sqrt(halfSquared) - 1.0E-9D);
    return Math.min(Math.max(inset, 0), empty);
  }

  /**
   * @return whether the stored data is usable, files edited by hand may miss fields
   */
  boolean isValid() {
    return this.id != null
        && this.shape != null
        && this.name != null
        && this.maxX > this.minX
        && this.maxZ > this.minZ;
  }

  public enum Shape {
    RECTANGLE,
    CIRCLE
  }
}
