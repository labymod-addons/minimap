package net.labymod.addons.minimap.api.map;

public class MinimapBounds {

  private int x1;
  private int z1;
  private int x2;
  private int z2;

  private int depth;

  public MinimapBounds(int x1, int z1, int x2, int z2, int depth) {
    this.x1 = x1;
    this.z1 = z1;
    this.x2 = x2;
    this.z2 = z2;
    this.depth = depth;
  }

  public MinimapBounds() {
  }

  public int getX1() {
    return this.x1;
  }

  public int getZ1() {
    return this.z1;
  }

  public int getX2() {
    return this.x2;
  }

  public int getZ2() {
    return this.z2;
  }

  public int getDepth() {
    return this.depth;
  }

  public int getXLength() {
    return this.x2 - this.x1;
  }

  public int getZLength() {
    return this.z2 - this.z1;
  }

  public void update(int x1, int z1, int x2, int z2, int depth) {
    this.x1 = x1;
    this.z1 = z1;
    this.x2 = x2;
    this.z2 = z2;

    this.depth = depth;
  }

  public void update(MinimapBounds bounds) {
    this.x1 = bounds.getX1();
    this.z1 = bounds.getZ1();
    this.x2 = bounds.getX2();
    this.z2 = bounds.getZ2();

    this.depth = bounds.getDepth();
  }

  public boolean equals(int x1, int z1, int x2, int z2) {
    return this.x1 == x1 && this.z1 == z1 && this.x2 == x2 && this.z2 == z2;
  }

  public boolean equalsDepthRange(int depth, int range) {
    return Math.abs(this.depth - depth) < range;
  }

  public void clear() {
    this.x1 = 0;
    this.z1 = 0;
    this.x2 = 0;
    this.z2 = 0;

    this.depth = 0;
  }
}
