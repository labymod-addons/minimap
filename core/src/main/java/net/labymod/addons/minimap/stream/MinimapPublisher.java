package net.labymod.addons.minimap.stream;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.MinimapContext;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.ChunkDataStorage;
import net.labymod.addons.waypoints.Waypoints;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.world.MinecraftCamera;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.externaldevice.ExternalDeviceService;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;
import net.labymod.api.util.math.vector.DoubleVector3;

/**
 * Publishes the minimap to phones via the core {@link ExternalDeviceService}: throttled live state
 * (position, heading, players, waypoints) plus delta-encoded map tiles (see
 * {@link MinimapChannel}). Transport, pairing and generic widget streaming live in the core — this
 * class only contributes the minimap's frames while a device is connected.
 *
 * <p>Respects {@link MinimapAddon#isMinimapAllowed()}: on blacklisted servers no tiles are sent and
 * the state carries {@code allowed:false}.
 */
public class MinimapPublisher {

  /** Only stream chunks within this Chebyshev radius (in chunks) of the player. */
  private static final int TILE_RADIUS_CHUNKS = 12;
  /** Cap tiles per pass so a fresh area streams over a few ticks instead of one huge burst. */
  private static final int MAX_TILES_PER_TICK = 8;
  /**
   * Nearest-neighbour upscale factor for tile PNGs (16&times;16 blocks &rarr; 128&times;128 px).
   * The phone renders tiles at ~12+ physical px per block and its image pipeline only smooths
   * (no nearest-neighbour sampling), so 1&nbsp;px per block arrived visibly blurry. Flat colour
   * squares compress extremely well, so the PNGs stay small.
   */
  private static final int TILE_SCALE = 8;

  private final MinimapAddon addon;
  private final ChunkDataStorage storage;
  private final Map<Long, Integer> tileHashes = new HashMap<>();

  private int tickCounter;
  private int lastGeneration = -1;
  /**
   * Synthetic stream clock: advances EXACTLY 50ms per game tick, independent of wall time. Real
   * tick scheduling jitters (30–70ms with catch-up bursts) while positions advance one fixed step
   * per tick — stamping wall time therefore made the apparent speed wobble on the phone.
   */
  private long streamTime;

  public MinimapPublisher(MinimapAddon addon, MinimapContext context) {
    this.addon = addon;
    this.storage = context.storage();
  }

  @Subscribe
  public void onTick(GameTickEvent event) {
    if (event.phase() != Phase.POST) {
      return;
    }
    ExternalDeviceService service = Laby.references().externalDeviceService();
    if (!service.hasConnectedDevice()) {
      return;
    }

    // A new device paired since our last pass → resend every tile, not just deltas.
    int generation = service.connectionGeneration();
    if (generation != this.lastGeneration) {
      this.lastGeneration = generation;
      this.tileHashes.clear();
    }

    this.tickCounter++;
    this.streamTime += 50; // one game tick on the stream timeline
    boolean allowed = this.addon.isMinimapAllowed();

    if (this.tickCounter % Math.max(1, 20 / MinimapChannel.STATE_HZ) == 0) {
      service.publishText(buildState(allowed));
    }

    if (allowed && this.tickCounter % Math.max(1, 20 / MinimapChannel.TILE_HZ) == 0) {
      streamTiles(service);
    }
  }

  // ---- state -------------------------------------------------------------------------------------

  private String buildState(boolean allowed) {
    Minecraft minecraft = Laby.labyAPI().minecraft();
    ClientPlayer player = minecraft.getClientPlayer();

    StringBuilder json = new StringBuilder(256);
    // Tick-time stamp (see streamTime): the app's jitter-buffer playhead runs on THIS timeline,
    // so neither delivery jitter nor wall-clock tick scheduling can distort the motion.
    json.append("{\"t\":\"").append(MinimapChannel.MSG_STATE).append("\",\"ts\":")
        .append(this.streamTime)
        .append(",\"allowed\":").append(allowed);

    if (player != null) {
      Position position = player.position();
      MinecraftCamera camera = minecraft.getCamera();
      float yaw = camera == null ? 0F : camera.getYaw();
      json.append(",\"x\":").append(num(position.getX()))
          .append(",\"y\":").append(num(position.getY()))
          .append(",\"z\":").append(num(position.getZ()))
          .append(",\"yaw\":").append(num(yaw));
    }

    json.append(",\"players\":[");
    appendPlayers(json, player);
    json.append("],\"waypoints\":[");
    appendWaypoints(json);
    json.append("]}");
    return json.toString();
  }

  private void appendPlayers(StringBuilder json, ClientPlayer self) {
    boolean first = true;
    for (Player player : Laby.references().clientWorld().getPlayers()) {
      if (player == self) {
        continue;
      }
      Position position = player.position();
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append("{\"u\":\"").append(player.getUniqueId())
          .append("\",\"n\":\"").append(Json.escape(player.getName()))
          .append("\",\"x\":").append(num(position.getX()))
          .append(",\"z\":").append(num(position.getZ())).append('}');
    }
  }

  private void appendWaypoints(StringBuilder json) {
    try {
      boolean first = true;
      for (var waypoint : Waypoints.references().waypointService().getVisible()) {
        DoubleVector3 position = waypoint.position();
        if (!first) {
          json.append(',');
        }
        first = false;
        json.append("{\"x\":").append(num(position.getX()))
            .append(",\"z\":").append(num(position.getZ()))
            .append(",\"color\":").append(waypoint.meta().iconColor()).append('}');
      }
    } catch (Throwable ignored) {
      // waypoints addon not present or not ready yet, skip silently
    }
  }

  // ---- tiles -------------------------------------------------------------------------------------

  private void streamTiles(ExternalDeviceService service) {
    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return;
    }
    int centerChunkX = MathHelper.floor(player.position().getX()) >> 4;
    int centerChunkZ = MathHelper.floor(player.position().getZ()) >> 4;

    // Forget sent-hashes for chunks well outside the streaming radius: the app prunes distant
    // tiles from memory, so those areas MUST re-stream when the player returns — a permanent
    // hash entry would suppress the resend forever (visible as tiles vanishing for good).
    this.tileHashes.keySet().removeIf(key -> {
      int keyX = (int) (key >> 32);
      int keyZ = (int) (long) key;
      return Math.abs(keyX - centerChunkX) > TILE_RADIUS_CHUNKS + 4
          || Math.abs(keyZ - centerChunkZ) > TILE_RADIUS_CHUNKS + 4;
    });

    int sent = 0;
    for (ChunkData data : this.storage.getChunks()) {
      if (Math.abs(data.getX() - centerChunkX) > TILE_RADIUS_CHUNKS
          || Math.abs(data.getZ() - centerChunkZ) > TILE_RADIUS_CHUNKS) {
        continue;
      }
      if (!this.storage.isCompiled(data)) {
        continue;
      }
      long key = chunkKey(data.getX(), data.getZ());
      int hash = colorHash(data);
      Integer previous = this.tileHashes.get(key);
      if (previous != null && previous == hash) {
        continue;
      }

      byte[] frame = encodeTile(data);
      if (frame != null) {
        service.publishBinary(frame);
        this.tileHashes.put(key, hash);
        if (++sent >= MAX_TILES_PER_TICK) {
          break;
        }
      }
    }
  }

  private byte[] encodeTile(ChunkData data) {
    try {
      int size = 16 * TILE_SCALE;
      int[] pixels = new int[size * size];
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 16; x++) {
          int color = data.getColor(x, z);
          int baseX = x * TILE_SCALE;
          int baseY = z * TILE_SCALE;
          for (int dy = 0; dy < TILE_SCALE; dy++) {
            int row = (baseY + dy) * size + baseX;
            for (int dx = 0; dx < TILE_SCALE; dx++) {
              pixels[row + dx] = color;
            }
          }
        }
      }
      BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
      image.setRGB(0, 0, size, size, pixels, 0, size);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(image, "png", png);
      byte[] pngBytes = png.toByteArray();

      ByteArrayOutputStream out = new ByteArrayOutputStream(pngBytes.length + 9);
      DataOutputStream frame = new DataOutputStream(out);
      frame.writeByte(MinimapChannel.BINARY_TILE);
      frame.writeInt(data.getX());
      frame.writeInt(data.getZ());
      frame.write(pngBytes);
      return out.toByteArray();
    } catch (IOException exception) {
      return null;
    }
  }

  private static int colorHash(ChunkData data) {
    int hash = 1;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        hash = 31 * hash + data.getColor(x, z);
      }
    }
    return hash;
  }

  private static long chunkKey(int x, int z) {
    return (long) x << 32 | z & 0xFFFFFFFFL;
  }

  private static String num(double value) {
    return String.format(Locale.US, "%.2f", value);
  }
}
