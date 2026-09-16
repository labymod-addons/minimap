package net.labymod.addons.minimap.data.compilation;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.chunk.Chunk;

/**
 * Decides whether the current dimension has a roof like the nether's. A chunk counts as roofed
 * when most of its sampled columns have bedrock as their highest visible block. Chunks without
 * blocks yet don't count.
 */
public final class RoofDetector {

  private static final ResourceLocation BEDROCK = ResourceLocation.create("minecraft", "bedrock");
  private static final int SAMPLE_CHUNKS = 8;
  private static final int DECISION_TICKS = 40;
  private static final int[] SAMPLE_COLUMNS = {4, 12};

  private final GameChunkCompiler compiler = new GameChunkCompiler();
  private final LongSet sampled = new LongOpenHashSet();
  private boolean decided;
  private boolean roofed;
  private int votes;
  private int ticks;

  public void reset() {
    this.sampled.clear();
    this.decided = false;
    this.roofed = false;
    this.votes = 0;
    this.ticks = 0;
  }

  public boolean isDecided() {
    return this.decided;
  }

  public boolean isRoofed() {
    return this.roofed;
  }

  /**
   * Decides with fewer samples after {@value #DECISION_TICKS} ticks, so worlds with only a few
   * loaded chunks still get a decision.
   */
  public void tick() {
    if (!this.decided && ++this.ticks >= DECISION_TICKS && !this.sampled.isEmpty()) {
      this.decide();
    }
  }

  public void sample(Chunk chunk) {
    long key = Util.getChunkId(chunk.getChunkX(), chunk.getChunkZ());
    if (this.decided || this.sampled.contains(key)) {
      return;
    }

    int bedrockColumns = 0;
    int visibleColumns = 0;
    for (int x : SAMPLE_COLUMNS) {
      for (int z : SAMPLE_COLUMNS) {
        BlockState top = this.compiler.topVisibleBlock(chunk, x, z);
        if (top == null) {
          continue;
        }

        visibleColumns++;
        if (top.block().id().equals(BEDROCK)) {
          bedrockColumns++;
        }
      }
    }

    if (visibleColumns == 0) {
      return;
    }

    this.sampled.add(key);
    if (bedrockColumns * 2 > visibleColumns) {
      this.votes++;
    }

    if (this.sampled.size() >= SAMPLE_CHUNKS) {
      this.decide();
    }
  }

  /**
   * @return whether the column has no visible block at or above {@code blockY}, e.g. a player
   *     standing on the roof
   */
  public boolean isAboveTop(Chunk chunk, int blockX, int blockY, int blockZ) {
    BlockState top = this.compiler.topVisibleBlock(chunk, blockX & 15, blockZ & 15);
    return top == null || top.position().getY() < blockY;
  }

  private void decide() {
    this.roofed = this.votes * 2 > this.sampled.size();
    this.decided = true;
    this.sampled.clear();
  }
}
