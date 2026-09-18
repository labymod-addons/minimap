package net.labymod.addons.minimap.data.compilation;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.GameChunkData;
import net.labymod.api.Laby;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.block.Block;
import net.labymod.api.client.world.block.BlockColorProvider;
import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.generated.ReferenceStorage;
import net.labymod.api.util.ColorUtil;
import net.labymod.api.util.color.format.ColorFormat;
import net.labymod.api.util.math.vector.IntVector3;
import org.jetbrains.annotations.Nullable;

public class GameChunkCompiler implements ChunkCompiler<GameChunkData> {

  private static final Set<ResourceLocation> IGNORED_BLOCKS = Set.of(
      ResourceLocation.create("minecraft", "barrier"),
      ResourceLocation.create("minecraft", "light")
  );
  private static final int CAVE_HEIGHT = 8;
  private static final int CAVE_DEPTH = 24;
  private static final float CAVE_MIN_BRIGHTNESS = 0.35F;
  private static final int CAVE_ROCK_COLOR = 0xFF000000;
  // Not pure black, so fully solid chunks aren't mistaken for chunks without blocks
  private static final int ROOF_ROCK_COLOR = 0xFF1C1616;
  private static final int WATER_MAX_DEPTH = 10;
  private static final int DEFAULT_BIOME_BLEND = 2;
  private final Map<Block, Boolean> visibilityCache = new IdentityHashMap<>();
  private final BlockColorProvider blockColorProvider;
  private final ClientWorld level;
  private int playerX;
  private int playerY;
  private int playerZ;
  private boolean underground;
  private boolean roofed;
  private int biomeBlend = DEFAULT_BIOME_BLEND;

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

  /**
   * In a roofed dimension like the nether the surface is the first floor below the roof.
   */
  @Override
  public void setRoofed(boolean roofed) {
    this.roofed = roofed;
  }

  @Override
  public void setBiomeBlend(int radius) {
    this.biomeBlend = radius;
  }

  /**
   * @return the highest visible block of the column, or {@code null} if it has none
   */
  @Nullable
  public BlockState topVisibleBlock(Chunk chunk, int x, int z) {
    BlockState top = this.getBlockState(chunk, x, z);
    return top != null && this.isVisible(top) ? top : null;
  }

  /**
   * Looks for open space near the player's level, first up to {@link #CAVE_HEIGHT} blocks above,
   * then up to {@link #CAVE_DEPTH} below. The floor under it gets darker the further it is from
   * the player. Columns with no open space in that range render as rock.
   */
  private void compileUndergroundChunk(GameChunkData data, ColorFormat format, int x, int z) {
    Chunk chunk = data.getChunk();
    int minY = Math.max(this.level.getMinBuildHeight(), this.playerY - CAVE_DEPTH);
    int openY = this.findOpenY(chunk, x, z, minY);

    for (int y = openY; y >= minY; y--) {
      BlockState state = chunk.getBlockState(x, y, z);
      if (!this.isVisible(state)) {
        continue;
      }

      this.compileChunkColor(data, format, x, z, state, y, chunk.getBlockState(x, y + 1, z));

      int distance = Math.min(Math.abs(this.playerY - y), CAVE_DEPTH);
      float brightness = 1.0F - (1.0F - CAVE_MIN_BRIGHTNESS) * distance / CAVE_DEPTH;
      data.setColor(
          x, z,
          format.mul(data.getColor(x, z), brightness, brightness, brightness, 1.0F)
      );
      return;
    }

    data.setHeight(x, z, minY);
    data.setLightLevel(x, z, 0);
    data.setColor(x, z, CAVE_ROCK_COLOR);
  }

  /**
   * @return the open block nearest to the player's level, or {@code minY - 1} if there is none
   */
  private int findOpenY(Chunk chunk, int x, int z, int minY) {
    int maxY = this.playerY + CAVE_HEIGHT;
    for (int y = this.playerY; y <= maxY; y++) {
      if (this.isOpen(chunk.getBlockState(x, y, z))) {
        return y;
      }
    }

    for (int y = this.playerY - 1; y >= minY; y--) {
      if (this.isOpen(chunk.getBlockState(x, y, z))) {
        return y;
      }
    }

    return minY - 1;
  }

  /**
   * The map skips blocks without a color, like fences and doors, and blocks
   * without collision, like grass and crops. It shows the block below them instead. Fluids and
   * rails stay visible.
   */
  private boolean isVisible(BlockState state) {
    if (state == null) {
      return false;
    }

    Block block = state.block();
    if (block.isAir()) {
      return false;
    }

    Boolean visible = this.visibilityCache.get(block);
    if (visible == null) {
      // LabyMod returns -1 for blocks without a color. Builds before the 26.x fix return 0.
      int color = this.blockColorProvider.getColor(state);
      visible = !IGNORED_BLOCKS.contains(block.id())
          && (state.hasCollision() || state.isFluid() || state.isRail())
          && color != 0 && color != -1;
      this.visibilityCache.put(block, visible);
    }

    return visible;
  }

  private boolean isOpen(BlockState state) {
    return state == null || state.block().isAir() || !state.hasCollision();
  }

  private void compileOverworldChunk(
      GameChunkData data,
      ColorFormat format,
      int x, int z
  ) {
    Chunk chunk = data.getChunk();
    BlockState block = this.roofed ? this.getBlockUnderRoof(chunk, x, z) : this.getBlockState(chunk, x, z);
    if (block == null && this.roofed) {
      data.setHeight(x, z, this.level.getMinBuildHeight());
      data.setLightLevel(x, z, 0);
      data.setColor(x, z, ROOF_ROCK_COLOR);
      return;
    }

    if (block == null) {
      data.setColor(x, z, 0xFF000000);
      return;
    }

    BlockState above = this.getBlockAbove(chunk, block);
    this.compileChunkColor(
        data,
        format,
        x, z,
        block,
        () -> block.position().getY() - (block.hasCollision() ? 0 : 1),
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
      int surfaceY = block.position().getY();
      BlockState blockStateUnderWater = this.getBlockBelow(
          data.getChunk(),
          block,
          state -> !state.isWater() && this.isVisible(state)
      );

      // Deeper water hides more of the floor and gets darker
      int depth = Math.min(surfaceY - blockStateUnderWater.position().getY(), WATER_MAX_DEPTH);
      float brightness = 1.0F - 0.35F * depth / WATER_MAX_DEPTH;
      int alpha = 150 + 95 * depth / WATER_MAX_DEPTH;
      baseColor = format.pack(
          format.mul(baseColor, brightness, brightness, brightness, 1.0F),
          alpha
      );

      int colorUnderWater = format.withAlpha(this.getColor(format, blockStateUnderWater), 255);
      baseColor = ColorUtil.blendColors(colorUnderWater, baseColor);
      // Surface height keeps the floor relief from shading the water
      data.setHeight(x, z, surfaceY);
    }

    data.setColor(x, z, baseColor);
  }

  private int getBaseColor(ColorFormat format, BlockState state) {
    return format.withAlpha(this.getColor(format, state), 255);
  }

  private int getColor(ColorFormat format, BlockState state) {
    int baseColor = this.blockColorProvider.getColor(state);
    // LabyMod averages along a cross around the block, the z range is exclusive at the end
    int multiplier = this.blockColorProvider.getColorMultiplier(
        state,
        -this.biomeBlend, -this.biomeBlend,
        this.biomeBlend, this.biomeBlend + 1
    );

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

      if (this.isVisible(blockState)) {
        break;
      }
      y--;
    }

    return blockState;
  }

  /**
   * Skips the open space above the roof and the solid roof itself.
   *
   * @return the first visible block below the roof, or {@code null} for a column that is solid
   *     down to the bottom
   */
  private BlockState getBlockUnderRoof(Chunk chunk, int x, int z) {
    int y = chunk.getHeightBasedOnSection(64);
    int minBuildHeight = this.level.getMinBuildHeight();
    while (y > minBuildHeight && this.isOpen(chunk.getBlockState(x, y, z))) {
      y--;
    }

    while (y > minBuildHeight && !this.isOpen(chunk.getBlockState(x, y, z))) {
      y--;
    }

    while (y > minBuildHeight) {
      BlockState state = chunk.getBlockState(x, y, z);
      if (this.isVisible(state)) {
        return state;
      }

      y--;
    }

    return null;
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
