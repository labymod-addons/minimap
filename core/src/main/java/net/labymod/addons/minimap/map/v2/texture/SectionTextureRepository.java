package net.labymod.addons.minimap.map.v2.texture;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Collection;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.api.client.world.chunk.Chunk;

public class SectionTextureRepository {

  public static final int SECTION_SIZE = 8;
  private final Long2ObjectMap<CompositeSectionTexture> textures;

  public SectionTextureRepository() {
    this.textures = new Long2ObjectOpenHashMap<>();
  }

  public CompositeSectionTexture getOrCreateSectionTexture(ChunkData data) {
    return this.getOrCreateSectionTexture(data.getX(), data.getZ());
  }

  public CompositeSectionTexture getOrCreateSectionTexture(Chunk chunk) {
    return this.getOrCreateSectionTexture(chunk.getChunkX(), chunk.getChunkZ());
  }

  public CompositeSectionTexture getOrCreateSectionTexture(int chunkX, int chunkZ) {
    int sectionX = Math.floorDiv(chunkX, SECTION_SIZE);
    int sectionZ = Math.floorDiv(chunkZ, SECTION_SIZE);
    return this.getOrCreate(sectionX, sectionZ);
  }

  public CompositeSectionTexture getSectionTexture(int sectionX, int sectionZ) {
    long key = this.getSectionKey(sectionX, sectionZ);
    return this.textures.get(key);
  }

  public CompositeSectionTexture getOrCreate(int x, int z) {
    long key = this.getSectionKey(x, z);
    CompositeSectionTexture texture = this.textures.get(key);
    if (texture == null) {
      texture = new CompositeSectionTexture(x, z, SECTION_SIZE);
      this.textures.put(key, texture);
    }

    return texture;
  }

  public Collection<CompositeSectionTexture> textures() {
    return this.textures.values();
  }

  private long getSectionKey(int x, int z) {
    return (long) x << 32 | z & 0xFFFFFFFFL;
  }

}
