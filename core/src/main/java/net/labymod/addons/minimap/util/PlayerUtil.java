package net.labymod.addons.minimap.util;

import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.world.ClientWorld;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.client.world.chunk.HeightmapType;
import net.labymod.api.client.world.lighting.LightType;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;
import org.jetbrains.annotations.Nullable;

public final class PlayerUtil {

  /**
   * A player counts as underground when no sky light reaches their eyes and the world surface is
   * more than {@code threshold} blocks above their feet. Requiring both keeps houses, trees and
   * walls from triggering cave mode.
   */
  public static boolean isPlayerUnderground(
      ClientWorld level,
      @Nullable ClientPlayer player,
      int threshold
  ) {
    return isPlayerUndergroundBySkylight(level, player)
        && isPlayerUndergroundBySurface(level, player, threshold);
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

}
