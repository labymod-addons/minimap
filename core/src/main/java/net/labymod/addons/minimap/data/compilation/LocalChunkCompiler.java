package net.labymod.addons.minimap.data.compilation;

import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.GameChunkData;
import net.labymod.addons.minimap.data.LocalChunkData;

public class LocalChunkCompiler implements ChunkCompiler<GameChunkData> {

  @Override
  public boolean isCompatible(ChunkData data) {
    return data instanceof GameChunkData;
  }

  @Override
  public void compile(GameChunkData data) {
    LocalChunkData localChunkData = new LocalChunkData();
    localChunkData.setPosition(data.getX(), data.getZ());

    for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
        localChunkData.setColor(x, z, data.getColor(x, z));
        localChunkData.setHeight(x, z, data.getHeight(x, z));
        localChunkData.setLightLevel(x, z, data.getLightLevel(x, z));
      }
    }
  }
}
