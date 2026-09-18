package net.labymod.addons.minimap.worldmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.world.MapWorldKey;
import net.labymod.api.util.I18n;
import org.jetbrains.annotations.Nullable;

final class WorldMapNames {

  private static final String I18N_PREFIX = Util.NAMESPACE + ".worldMap.";
  private static final String OVERWORLD = "minecraft:overworld";
  private static final String NETHER = "minecraft:the_nether";
  private static final double NETHER_SCALE = 8.0D;
  private static final List<String> VANILLA_DIMENSIONS = List.of(
      OVERWORLD,
      NETHER,
      "minecraft:the_end"
  );
  private static final Comparator<MapWorldKey> DIMENSION_ORDER = Comparator
      .comparingInt((MapWorldKey key) -> dimensionRank(key.dimension()))
      .thenComparing(MapWorldKey::dimension);

  private WorldMapNames() {
  }

  /**
   * Turns {@code minecraft:the_nether} into {@code The Nether}.
   */
  static String dimension(String id) {
    String path = id.substring(id.indexOf(':') + 1);
    StringBuilder name = new StringBuilder(path.length());
    boolean capitalize = true;
    for (int index = 0; index < path.length(); index++) {
      char character = path.charAt(index);
      if (character == '_' || character == '/') {
        name.append(' ');
        capitalize = true;
      } else {
        name.append(capitalize ? Character.toUpperCase(character) : character);
        capitalize = false;
      }
    }

    return name.toString();
  }

  /**
   * @param name the name the player gave it, {@code null} if none
   */
  /**
   * @return the dimension linked to this one by the 1:8 portal scale, {@code null} if none
   */
  @Nullable
  static String counterpart(String dimension) {
    if (dimension.equals(OVERWORLD)) {
      return NETHER;
    }

    return dimension.equals(NETHER) ? OVERWORLD : null;
  }

  /**
   * @return blocks in the {@link #counterpart} per block in this dimension
   */
  static double counterpartScale(String dimension) {
    return dimension.equals(NETHER) ? NETHER_SCALE : 1.0D / NETHER_SCALE;
  }

  static String subWorld(MapWorldKey key, @Nullable String name) {
    return name == null ? I18n.getTranslation(I18N_PREFIX + "subWorld", key.subWorld() + 1) : name;
  }

  /**
   * @return one key per dimension, with overworld, nether and end first. A dimension uses the
   *     shown or recorded key if it has one. Otherwise it uses its first sub-world.
   */
  static List<MapWorldKey> dimensionTabs(
      List<MapWorldKey> keys,
      MapWorldKey viewKey,
      @Nullable MapWorldKey activeKey
  ) {
    List<MapWorldKey> tabs = new ArrayList<>();
    for (MapWorldKey key : keys) {
      int index = indexOfDimension(tabs, key.dimension());
      if (index == -1) {
        tabs.add(key);
      } else if (key.equals(viewKey) || (key.equals(activeKey) && !tabs.get(index).equals(viewKey))) {
        tabs.set(index, key);
      }
    }

    tabs.sort(DIMENSION_ORDER);
    return tabs;
  }

  static List<MapWorldKey> subWorlds(List<MapWorldKey> keys, String dimension) {
    List<MapWorldKey> subWorlds = new ArrayList<>();
    for (MapWorldKey key : keys) {
      if (key.dimension().equals(dimension)) {
        subWorlds.add(key);
      }
    }

    return subWorlds;
  }

  private static int indexOfDimension(List<MapWorldKey> keys, String dimension) {
    for (int index = 0; index < keys.size(); index++) {
      if (keys.get(index).dimension().equals(dimension)) {
        return index;
      }
    }

    return -1;
  }

  private static int dimensionRank(String dimension) {
    int index = VANILLA_DIMENSIONS.indexOf(dimension);
    return index == -1 ? VANILLA_DIMENSIONS.size() : index;
  }
}
