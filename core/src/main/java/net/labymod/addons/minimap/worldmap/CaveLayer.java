package net.labymod.addons.minimap.worldmap;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Arrays;
import java.util.Set;
import net.labymod.addons.minimap.data.GameChunkData;
import net.labymod.addons.minimap.data.compilation.GameChunkCompiler;
import net.labymod.addons.minimap.laby3d.MinimapUniformBlocks;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.map.v2.texture.CompositeSectionTexture;
import net.labymod.addons.minimap.map.v2.texture.SectionTextureRepository;
import net.labymod.addons.minimap.world.MapRegion;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.gui.screen.ScreenContext;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;

/**
 * Caves around the player's height in the loaded chunks, drawn over the saved surface. Rebuilds
 * with a crossfade when the player moves up or down.
 */
final class CaveLayer {

  private static final long BUILD_BUDGET_NANOS = 3_000_000L;
  private static final int REBUILD_HEIGHT_DIFFERENCE = 3;

  private final MinimapRenderer minimapRenderer;
  private final MinimapUniformBlocks uniformBlocks;
  private final SectionTextureRepository sections = new SectionTextureRepository();
  private final GameChunkCompiler compiler = new GameChunkCompiler();
  private final LongSet built = new LongOpenHashSet();
  private Chunk[] pending = new Chunk[0];
  private long[] pendingOrder = new long[0];
  private boolean hasBuilt;
  private int builtY;

  CaveLayer(MinimapRenderer minimapRenderer, MinimapUniformBlocks uniformBlocks) {
    this.minimapRenderer = minimapRenderer;
    this.uniformBlocks = uniformBlocks;
  }

  /**
   * @param biomeBlend blocks that grass and foliage colors are averaged over
   */
  void tick(ClientWorld world, ClientPlayer player, int biomeBlend) {
    this.compiler.setBiomeBlend(biomeBlend);
    Position position = player.position();
    int playerY = MathHelper.floor(position.getY());
    if (!this.hasBuilt || Math.abs(playerY - this.builtY) >= REBUILD_HEIGHT_DIFFERENCE) {
      this.hasBuilt = true;
      this.builtY = playerY;
      this.built.clear();
      for (CompositeSectionTexture texture : this.sections.textures()) {
        texture.beginTransition();
      }
    }

    this.compiler.setPlayerPosition(
        MathHelper.floor(position.getX()),
        this.builtY,
        MathHelper.floor(position.getZ()),
        true
    );

    int count = this.collectPending(
        world,
        MathHelper.floor(position.getX()) >> 4,
        MathHelper.floor(position.getZ()) >> 4
    );

    int minBuildHeight = world.getMinBuildHeight();
    long deadline = System.nanoTime() + BUILD_BUDGET_NANOS;
    for (int index = 0; index < count; index++) {
      Chunk chunk = this.pending[(int) this.pendingOrder[index]];
      this.built.add(MapRegion.key(chunk.getChunkX(), chunk.getChunkZ()));

      GameChunkData data = new GameChunkData(chunk);
      this.compiler.compile(data);
      MinimapRenderer.writeChunk(
          this.sections,
          chunk.getChunkX(), chunk.getChunkZ(),
          data,
          minBuildHeight
      );

      if (System.nanoTime() >= deadline) {
        break;
      }
    }

    Arrays.fill(this.pending, 0, count, null);
  }

  /**
   * Collects the loaded chunks that still need building, nearest to the player first, so the cave
   * view grows outwards from the player instead of appearing in random spots.
   *
   * @return the number of pending chunks
   */
  private int collectPending(ClientWorld world, int centerChunkX, int centerChunkZ) {
    Set<Chunk> chunks = world.getChunks();
    if (this.pending.length < chunks.size()) {
      this.pending = new Chunk[chunks.size()];
      this.pendingOrder = new long[chunks.size()];
    }

    int count = 0;
    for (Chunk chunk : chunks) {
      if (count == this.pending.length) {
        break;
      }

      if (this.built.contains(MapRegion.key(chunk.getChunkX(), chunk.getChunkZ()))) {
        continue;
      }

      long deltaX = chunk.getChunkX() - centerChunkX;
      long deltaZ = chunk.getChunkZ() - centerChunkZ;
      this.pending[count] = chunk;
      this.pendingOrder[count] = (deltaX * deltaX + deltaZ * deltaZ) << 32 | count;
      count++;
    }

    Arrays.sort(this.pendingOrder, 0, count);
    return count;
  }

  void render(ScreenContext context, WorldMapCamera camera, float width, float height) {
    if (!this.minimapRenderer.applyUniforms()) {
      return;
    }

    int minBlockX = MathHelper.floor(camera.screenToWorldX(0.0F, width));
    int minBlockZ = MathHelper.floor(camera.screenToWorldZ(0.0F, height));
    int maxBlockX = MathHelper.floor(camera.screenToWorldX(width, width)) + 1;
    int maxBlockZ = MathHelper.floor(camera.screenToWorldZ(height, height)) + 1;
    MinimapRenderer.renderSections(
        context,
        this.sections,
        this.uniformBlocks,
        minBlockX, minBlockZ, maxBlockX, maxBlockZ,
        camera.worldToScreenX(minBlockX, width),
        camera.worldToScreenY(minBlockZ, height),
        (maxBlockX - minBlockX) * camera.scale(),
        (maxBlockZ - minBlockZ) * camera.scale()
    );
  }

  void dispose() {
    this.sections.disposeAll();
    this.built.clear();
    this.hasBuilt = false;
  }
}
