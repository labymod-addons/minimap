package net.labymod.addons.minimap.util;

import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.block.BlockState;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.client.world.chunk.HeightmapType;
import net.labymod.api.client.world.lighting.LightType;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;
import org.jetbrains.annotations.Nullable;

public final class PlayerUtil {

  public static boolean isPlayerUnderground(
      ClientWorld level,
      @Nullable ClientPlayer player,
      int threshold
  ) {
    return isPlayerUndergroundBySkylight(level, player)
        || isPlayerUndergroundByObstruction(level, player, threshold);
  }

  private static boolean isPlayerUndergroundBySkylight(
      ClientWorld level,
      @Nullable ClientPlayer player
  ) {
    if (player == null) {
      return false;
    }

    Position position = player.position();
    int x = MathHelper.floor(position.getX());
    int y = MathHelper.floor(position.getY() + player.getEyeHeight());
    int z = MathHelper.floor(position.getZ());

    int skyLight = level.getBlockState(x, y, z).getLightLevel(LightType.SKY);
    return skyLight == 0;
  }

  private static boolean isPlayerUndergroundBySurface(
      ClientWorld level,
      @Nullable ClientPlayer player,
      int threshold
  ) {
    if (player == null) {
      return false;
    }

    Position position = player.position();
    int x = MathHelper.floor(position.getX());
    int y = MathHelper.floor(position.getY());
    int z = MathHelper.floor(position.getZ());

    int chunkX = x >> 4;
    int chunkZ = z >> 4;
    int inChunkX = x & 15;
    int inChunkZ = z & 15;

    Chunk chunk = level.getChunk(chunkX, chunkZ);
    if (chunk == null) {
      return false;
    }

    int surfaceY = chunk.heightmap(HeightmapType.WORLD_SURFACE).getHeight(inChunkX, inChunkZ);
    return (surfaceY - y) > threshold;
  }

  private static boolean isPlayerUndergroundByObstruction(
      ClientWorld level,
      @Nullable ClientPlayer player,
      int scanUp
  ) {
    if (player == null) {
      return false;
    }

    Position position = player.position();
    int x = MathHelper.floor(position.getX());
    int y = MathHelper.floor(position.getY() + player.getEyeHeight());
    int z = MathHelper.floor(position.getZ());

    int maxY = level.getMaxBuildHeight();

    // scan a 3x3 area
    for (int scanX = x - 1; x <= Math.min(maxY, x + scanUp); x++) {
      for (int scanZ = z - 1; z <= Math.min(maxY, z + scanUp); z++) {
        BlockState state = level.getBlockState(scanX, y, scanZ);
        if (state != null && !state.block().isAir()) {
          // Found a ceiling close above
          return true;
        }
      }
    }

    return false;
  }

}
