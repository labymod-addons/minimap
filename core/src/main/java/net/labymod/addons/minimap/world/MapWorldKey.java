package net.labymod.addons.minimap.world;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Identifies one saved map. The context is the server address or the singleplayer save folder.
 * A lobby and a game server behind one address share context and dimension, so the sub-world
 * index tells them apart.
 */
public record MapWorldKey(boolean multiplayer, String context, String dimension, int subWorld) {

  public static final int UNRESOLVED_SUB_WORLD = -1;

  public MapWorldKey withSubWorld(int subWorld) {
    return new MapWorldKey(this.multiplayer, this.context, this.dimension, subWorld);
  }

  public boolean sameWorld(MapWorldKey other) {
    return other != null
        && this.multiplayer == other.multiplayer
        && this.context.equals(other.context);
  }

  public boolean sameDimension(MapWorldKey other) {
    return this.sameWorld(other) && this.dimension.equals(other.dimension);
  }

  public Path worldDirectory(Path root) {
    return root.resolve(this.multiplayer ? "mp" : "sp").resolve(sanitize(this.context));
  }

  public Path dimensionDirectory(Path root) {
    return this.worldDirectory(root).resolve(sanitize(this.dimension));
  }

  public Path directory(Path root) {
    return this.dimensionDirectory(root).resolve(Integer.toString(this.subWorld));
  }

  private static String sanitize(String value) {
    StringBuilder builder = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      boolean allowed = (character >= 'a' && character <= 'z')
          || (character >= 'A' && character <= 'Z')
          || (character >= '0' && character <= '9')
          || character == '-'
          || (character == '.' && index > 0);
      builder.append(allowed ? character : '_');
    }

    return builder.toString().toLowerCase(Locale.ROOT);
  }
}
