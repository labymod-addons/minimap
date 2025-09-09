package net.labymod.addons.minimap;

import net.labymod.addons.minimap.data.ChunkDataStorage;
import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.map.v2.texture.SectionTextureRepository;

public record MinimapContext(
    ChunkDataStorage storage,
    SectionTextureRepository sectionTextureRepository,
    MinimapUniformBlocks uniformBlocks
) {

  public MinimapContext() {
    this(new ChunkDataStorage(), new SectionTextureRepository(), new MinimapUniformBlocks());
  }
}
