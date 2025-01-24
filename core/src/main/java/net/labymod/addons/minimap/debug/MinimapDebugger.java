package net.labymod.addons.minimap.debug;

public class MinimapDebugger {

  public static final int INVALID_TEXTURE_ID = -1;
  public static final TextureInfo COLOR_MAP_TEXTURE = new TextureInfo(INVALID_TEXTURE_ID);
  public static final TextureInfo HEIGHTMAP_TEXTURE = new TextureInfo(INVALID_TEXTURE_ID);
  public static final TextureInfo LIGHTMAP_TEXTURE = new TextureInfo(INVALID_TEXTURE_ID);

  public static class TextureInfo {

    private int id;
    private int width;
    private int height;

    public TextureInfo(int id) {
      this(id, 0, 0);
    }

    public TextureInfo(int id, int width, int height) {
      this.id = id;
      this.width = width;
      this.height = height;
    }

    public int getId() {
      return this.id;
    }

    public void setId(int id) {
      this.id = id;
    }

    public int getWidth() {
      return this.width;
    }

    public void setWidth(int width) {
      this.width = width;
    }

    public int getHeight() {
      return this.height;
    }

    public void setHeight(int height) {
      this.height = height;
    }

    public void setSize(int width, int height) {
      this.setWidth(width);
      this.setHeight(height);
    }
  }

}
