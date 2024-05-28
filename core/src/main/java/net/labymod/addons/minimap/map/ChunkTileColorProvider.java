package net.labymod.addons.minimap.map;

import net.labymod.api.client.world.chunk.Chunk;

public interface ChunkTileColorProvider {

  void getColor(Chunk chunk, int[] colors);

}
