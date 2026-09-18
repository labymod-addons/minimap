package net.labymod.addons.minimap.world;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Colors of the biome map mode. Vanilla biomes have fixed colors, other biomes get a hue from a
 * hash of their id. Nothing writes to the map after class initialization, so any thread may read it.
 */
final class BiomeColors {

  private static final Map<String, Integer> COLORS = new HashMap<>();

  static {
    put("ocean", 0x000070);
    put("deep_ocean", 0x000030);
    put("warm_ocean", 0x0000AC);
    put("lukewarm_ocean", 0x000090);
    put("deep_lukewarm_ocean", 0x000040);
    put("cold_ocean", 0x202070);
    put("deep_cold_ocean", 0x202038);
    put("frozen_ocean", 0x7070D6);
    put("deep_frozen_ocean", 0x404090);
    put("river", 0x0000FF);
    put("frozen_river", 0xA0A0FF);
    put("plains", 0x8DB360);
    put("sunflower_plains", 0xB5DB88);
    put("snowy_plains", 0xFFFFFF);
    put("ice_spikes", 0xB4DCDC);
    put("desert", 0xFA9418);
    put("swamp", 0x07F9B2);
    put("mangrove_swamp", 0x2CCC8E);
    put("forest", 0x056621);
    put("flower_forest", 0x2D8E49);
    put("birch_forest", 0x307444);
    put("old_growth_birch_forest", 0x589C6C);
    put("dark_forest", 0x40511A);
    put("pale_garden", 0xB9C0B5);
    put("taiga", 0x0B6659);
    put("snowy_taiga", 0x31554A);
    put("old_growth_pine_taiga", 0x596651);
    put("old_growth_spruce_taiga", 0x818E79);
    put("jungle", 0x537B09);
    put("sparse_jungle", 0x628B17);
    put("bamboo_jungle", 0x768E14);
    put("savanna", 0xBDB25F);
    put("savanna_plateau", 0xA79D64);
    put("windswept_savanna", 0xE5DA87);
    put("badlands", 0xD94515);
    put("eroded_badlands", 0xFF6D3D);
    put("wooded_badlands", 0xB09765);
    put("windswept_hills", 0x606060);
    put("windswept_gravelly_hills", 0x888888);
    put("windswept_forest", 0x507050);
    put("meadow", 0x83BB6D);
    put("cherry_grove", 0xF4B6D2);
    put("grove", 0x7BA589);
    put("snowy_slopes", 0xDCDCE6);
    put("frozen_peaks", 0xB0C8E8);
    put("jagged_peaks", 0xDCDCDC);
    put("stony_peaks", 0x9A9A8A);
    put("beach", 0xFADE55);
    put("snowy_beach", 0xFAF0C0);
    put("stony_shore", 0xA2A284);
    put("mushroom_fields", 0xFF00FF);
    put("dripstone_caves", 0x8E7256);
    put("lush_caves", 0x5D8C2E);
    put("deep_dark", 0x0A2A30);
    put("nether_wastes", 0xBF3B3B);
    put("soul_sand_valley", 0x5E3830);
    put("crimson_forest", 0xDD0808);
    put("warped_forest", 0x49907B);
    put("basalt_deltas", 0x403636);
    put("the_end", 0x8080FF);
    put("small_end_islands", 0x6A6ACC);
    put("end_midlands", 0x9E9EE0);
    put("end_highlands", 0xB4B4F0);
    put("end_barrens", 0x7070C0);
    put("the_void", 0x000000);
  }

  private BiomeColors() {
  }

  static int color(String biome) {
    Integer color = COLORS.get(biome.indexOf(':') == -1 ? "minecraft:" + biome : biome);
    return color == null ? hashColor(biome) : color;
  }

  private static void put(String path, int color) {
    COLORS.put("minecraft:" + path, color);
  }

  private static int hashColor(String biome) {
    float hue = (biome.hashCode() & 0xFFFF) / (float) 0x10000;
    return Color.HSBtoRGB(hue, 0.6F, 0.8F) & 0xFFFFFF;
  }
}
