package net.labymod.addons.minimap.data.compilation;

import java.util.function.Function;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.GameChunkData;
import net.labymod.api.Laby;
import net.labymod.api.client.world.ClientWorld;
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
    Chunk chunk = data.getChunk();
    ColorFormat format = ColorFormat.ARGB32;

    for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
        BlockState highestBlock = this.getBlockState(chunk, x, z);
        BlockState block = this.getBlockState(chunk, x, z);
        if (highestBlock == null || block == null) {
          data.setColor(x, z, 0xFF000000);
          continue;
        }

        int baseColor = format.withAlpha(this.getColor(format, block), 255);
        BlockState above = chunk.getBlockState(x, block.position().getY() + 1, z);

        data.setHeight(x, z,
            highestBlock.position().getY() - (highestBlock.hasCollision() ? 0 : 1));
        data.setLightLevel(x, z, above);
        if (block.isWater()) {
          BlockState blockStateUnderWater = this.getBlockBelow(chunk, block, state -> !state.isWater());

          baseColor = format.pack(baseColor, 220);

          int colorUnderWater = format.withAlpha(this.getColor(format, blockStateUnderWater), 255);
          baseColor = ColorUtil.blendColors(colorUnderWater, baseColor);
          data.setHeight(x, z, blockStateUnderWater.position().getY());
        }

        data.setColor(x, z, baseColor);
      }
    }
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

}
