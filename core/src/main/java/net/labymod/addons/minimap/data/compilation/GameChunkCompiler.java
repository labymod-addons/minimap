package net.labymod.addons.minimap.data.compilation;

import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.GameChunkData;
import net.labymod.api.Laby;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.block.Block;
import net.labymod.api.client.world.block.BlockColorProvider;
import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.generated.ReferenceStorage;
import net.labymod.api.util.ColorUtil;
import net.labymod.api.util.color.format.ColorFormat;
import net.labymod.api.util.math.vector.IntVector3;

public class GameChunkCompiler implements ChunkCompiler<GameChunkData> {

  private final BlockColorProvider blockColorProvider;
  private final ClientWorld level;
  private int playerX;
  private int playerY;
  private int playerZ;
  private boolean underground;

  public GameChunkCompiler() {
    ReferenceStorage references = Laby.references();
    this.blockColorProvider = references.blockColorProvider();
    this.level = references.clientWorld();
  }

  @Override
  public boolean isCompatible(ChunkData data) {
    return data instanceof GameChunkData;
  }

  @Override
  public void compile(GameChunkData data) {
    ColorFormat format = ColorFormat.ARGB32;
    for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
        if (this.underground) {
          this.compileUndergroundChunk(data, format, x, z);
        } else {
          this.compileOverworldChunk(data, format, x, z);
        }
      }
    }
  }

  public void setPlayerPosition(int x, int y, int z, boolean underground) {
    this.playerX = x;
    this.playerY = y;
    this.playerZ = z;
    this.underground = underground;
  }

  private void compileUndergroundChunk(GameChunkData data, ColorFormat format, int x, int z) {
    // Fallback/clear
    data.setColor(x, z, 0xFF000000);

    final Chunk chunk = data.getChunk();
    final int depth = this.playerY;

    final int minBuildHeight = this.level.getMinBuildHeight();
    final int minScanY = Math.max(minBuildHeight, depth - 20);
    final int maxScanY = depth + 2;

    // 1) Probe downward from depth to find first non-air within [-20 .. 0]
    int startY = depth;
    BlockState state = chunk.getBlockState(x, startY, z);
    while (startY > minScanY && (state == null || state.block().isAir())) {
      startY--;
      state = chunk.getBlockState(x, startY, z);
    }
    // Clamp if we went below window, and ensure we have the state for startY
    if (startY < minScanY) {
      startY = minScanY;
      state = chunk.getBlockState(x, startY, z);
    }

    // 2) Single upward pass with rolling window: (state, above)
    BlockState above = chunk.getBlockState(x, startY + 1, z);

    for (int y = startY; y <= maxScanY; y++) {
      // If current block is solid (non-air), decide rendering using "above"
      if (state != null && !state.block().isAir()) {
        if (above != null) {
          Block aboveBlock = above.block();
          if (!aboveBlock.isAir() && !above.isFluid()) {
            // Non-fluid solid ceiling directly above -> keep black and stop
            data.setColor(x, z, 0xFF000000);
            return;
          }
        }
        final int blockY = y;
        final BlockState blockAbove = above;
        this.compileChunkColor(
            data,
            format,
            x, z,
            state,
            () -> blockY,
            () -> blockAbove
        );
        return;
      }

      // Advance window: move up one, fetch next "above" only once
      final int nextY = y + 1;
      state = above;
      above = (nextY + 1 <= maxScanY + 1) ? chunk.getBlockState(x, nextY + 1, z) : null;
    }

    // Nothing renderable found in the scan window; keep default color
  }

  private void compileOverworldChunk(
      GameChunkData data,
      ColorFormat format,
      int x, int z
  ) {
    Chunk chunk = data.getChunk();
    BlockState highestBlock = this.getBlockState(chunk, x, z);
    BlockState block = this.getBlockState(chunk, x, z);
    if (highestBlock == null || block == null) {
      data.setColor(x, z, 0xFF000000);
      return;
    }

    BlockState above = this.getBlockAbove(chunk, block);
    this.compileChunkColor(
            data,
            format,
            x, z,
            block,
            () -> highestBlock.position().getY() - (highestBlock.hasCollision() ? 0 : 1),
            () -> above
        );
  }

  private void compileChunkColor(
      GameChunkData data,
      ColorFormat format,
      int x, int z,
      BlockState block,
      IntSupplier defaultHeight,
      Supplier<BlockState> lightLevelGetter
  ) {
    this.compileChunkColor(
        data,
        format,
        x, z,
        block,
        defaultHeight.getAsInt(),
        lightLevelGetter.get()
    );
  }

  private void compileChunkColor(
      GameChunkData data,
      ColorFormat format,
      int x, int z,
      BlockState block,
      int defaultHeight,
      BlockState lightLevelState
  ) {
    int baseColor = this.getBaseColor(format, block);

    data.setHeight(x, z, defaultHeight);
    data.setLightLevel(x, z, lightLevelState);
    if (block.isWater()) {
      BlockState blockStateUnderWater = this.getBlockBelow(
          data.getChunk(),
          block,
          state -> !state.isWater()
      );

      baseColor = format.pack(baseColor, 220);

      int colorUnderWater = format.withAlpha(this.getColor(format, blockStateUnderWater), 255);
      baseColor = ColorUtil.blendColors(colorUnderWater, baseColor);
      data.setHeight(x, z, blockStateUnderWater.position().getY());
    }

    data.setColor(x, z, baseColor);
  }

  private int getBaseColor(ColorFormat format, BlockState state) {
    return format.withAlpha(this.getColor(format, state), 255);
  }

  private int getColor(ColorFormat format, BlockState state) {
    int baseColor = this.blockColorProvider.getColor(state);
    int multiplier = this.blockColorProvider.getColorMultiplier(state);

    float redMultiplier = format.normalizedRed(multiplier);
    float greenMultiplier = format.normalizedGreen(multiplier);
    float blueMultiplier = format.normalizedBlue(multiplier);

    return format.mul(
        baseColor,
        redMultiplier,
        greenMultiplier,
        blueMultiplier,
        1.0F
    );
  }

  private BlockState getBlockState(Chunk chunk, int x, int z) {
    int y = chunk.getHeightBasedOnSection(64);

    int minBuildHeight = this.level.getMinBuildHeight();
    BlockState blockState = null;
    while (y > minBuildHeight) {
      blockState = chunk.getBlockState(x, y, z);

      if (blockState != null && !blockState.block().isAir()) {
        break;
      }
      y--;
    }

    return blockState;
  }

  private BlockState getBlockBelow(
      Chunk chunk,
      BlockState state,
      Function<BlockState, Boolean> filter
  ) {
    IntVector3 position = state.position();
    int x = position.getX() & 15;
    int y = position.getY();
    int z = position.getZ() & 15;

    int minBuildHeight = this.level.getMinBuildHeight();

    BlockState blockState = null;
    while (y > minBuildHeight) {
      blockState = chunk.getBlockState(x, y, z);
      if (filter.apply(blockState)) {
        break;
      }

      y--;
    }

    return blockState;
  }

  private BlockState getBlockBelow(Chunk chunk, BlockState state) {
    IntVector3 position = state.position();
    int x = position.getX() & 15;
    int y = position.getY();
    int z = position.getZ() & 15;

    return chunk.getBlockState(x, y - 1, z);
  }

  private BlockState getBlockAbove(Chunk chunk, BlockState state) {
    IntVector3 position = state.position();
    int x = position.getX() & 15;
    int y = position.getY();
    int z = position.getZ() & 15;

    return chunk.getBlockState(x, y + 1, z);
  }

}
