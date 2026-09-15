package net.labymod.addons.minimap.stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.labymod.addons.minimap.MinimapAddon;
import net.labymod.addons.minimap.MinimapContext;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.ChunkDataStorage;
import net.labymod.addons.minimap.hudwidget.MinimapHudWidget;
import net.labymod.addons.minimap.map.v2.MinimapRenderer;
import net.labymod.addons.minimap.world.MapRegion;
import net.labymod.addons.minimap.world.MapRegionStore;
import net.labymod.addons.minimap.world.WorldMapService;
import net.labymod.addons.waypoints.Waypoints;
import net.labymod.api.Laby;
import net.labymod.api.client.Minecraft;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.world.MinecraftCamera;
import net.labymod.api.event.Phase;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.event.labymod.externaldevice.ExternalDeviceConnectedEvent;
import net.labymod.api.externaldevice.ExternalDeviceService;
import net.labymod.api.externaldevice.ExternalDeviceStream;
import net.labymod.api.util.math.MathHelper;
import net.labymod.api.util.math.position.Position;
import net.labymod.api.util.math.vector.DoubleVector3;

/**
 * Publishes the minimap to phones via the core {@link ExternalDeviceService}: throttled live state
 * (position, heading, sky brightness, players, waypoints) plus delta-encoded map tiles as colour
 * PNGs and raw colour/height/light planes (see {@link MinimapChannel}). Transport, pairing and
 * generic widget streaming live in the core, this class only contributes the minimap's frames
 * while a device is connected.
 *
 * <p>The game thread only picks the changed chunks and copies their colours; encoding and
 * publishing happen on {@link #encoder}, see the field for why.
 *
 * <p>Respects {@link MinimapAddon#isMinimapAllowed()}: on blacklisted servers no tiles are sent and
 * the state carries {@code allowed:false}.
 */
public class MinimapPublisher {

  /** Only stream chunks within this Chebyshev radius (in chunks) of the player. */
  private static final int TILE_RADIUS_CHUNKS = 12;
  /** Cap tiles per pass so a fresh area streams over a few ticks instead of one huge burst. */
  private static final int MAX_TILES_PER_TICK = 8;
  /** Tiles waiting to be encoded; a full queue rejects the submission, see submitChunk. */
  private static final int MAX_QUEUED_TILES = 64;
  /** Keeps only the newest tile requests. The app only needs its current viewport. */
  private static final int MAX_PENDING_REQUESTS = 4;
  /**
   * Nearest-neighbour upscale factor for tile PNGs (16&times;16 blocks &rarr; 128&times;128 px).
   * The phone renders tiles at ~12+ physical px per block and its image pipeline only smooths
   * (no nearest-neighbour sampling), so 1&nbsp;px per block arrived visibly blurry. Flat colour
   * squares compress extremely well, so the PNGs stay small.
   */
  private static final int TILE_SCALE = 8;

  private final MinimapAddon addon;
  private final MinimapRenderer renderer;
  private final MinimapHudWidget hudWidget;
  private final ChunkDataStorage storage;
  private final WorldMapService worldMap;
  /** Written from the game thread, entries dropped from the encoder when a tile fails. */
  private final Map<Long, Integer> tileHashes = new ConcurrentHashMap<>();
  /**
   * PNG encoding never runs on the game thread: {@link ImageIO} spills the image through a temp
   * FILE, and that disk write stalled the render thread for seconds at a time (watchdog "RENDER
   * THREAD HANG DETECTED"). Single-threaded, so tiles still reach the device in order.
   */
  private final ThreadPoolExecutor encoder = new ThreadPoolExecutor(
      1,
      1,
      0L,
      TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(MAX_QUEUED_TILES),
      runnable -> {
        Thread thread = new Thread(runnable, "Minimap Tile Encoder");
        thread.setDaemon(true);
        return thread;
      }
  );
  private final Queue<TileRequest> tileRequests = new ConcurrentLinkedQueue<>();

  private int tickCounter;
  /** A device paired since the last pass: it needs every tile, not the deltas it never saw. */
  private volatile boolean resendTiles;
  /**
   * Synthetic stream clock: advances EXACTLY 50ms per game tick, independent of wall time. Real
   * tick scheduling jitters (30–70ms with catch-up bursts) while positions advance one fixed step
   * per tick, so stamping wall time made the apparent speed wobble on the phone.
   */
  private long streamTime;

  public MinimapPublisher(
      MinimapAddon addon,
      MinimapContext context,
      MinimapRenderer renderer,
      MinimapHudWidget hudWidget,
      WorldMapService worldMap
  ) {
    this.addon = addon;
    this.renderer = renderer;
    this.hudWidget = hudWidget;
    this.storage = context.storage();
    this.worldMap = worldMap;

    Laby.references().externalDeviceService().control()
        .registerCommand(MinimapChannel.CMD_TILES, this::requestTiles);
  }

  /** Fires on a network thread, so the tile cache is dropped on the next tick instead of here. */
  @Subscribe
  public void onDeviceConnected(ExternalDeviceConnectedEvent event) {
    this.resendTiles = true;
  }

  @Subscribe
  public void onTick(GameTickEvent event) {
    if (event.phase() != Phase.POST) {
      return;
    }
    ExternalDeviceService service = Laby.references().externalDeviceService();
    if (!service.hasConnectedDevice()) {
      this.renderer.setMinimumBuildRadius(0);
      this.tileRequests.clear();
      return;
    }
    ExternalDeviceStream stream = service.stream();

    // Only compiled chunks are streamed, and chunks are compiled by the renderer tick. The HUD
    // widget drives that tick only while it's enabled, so drive it here otherwise.
    this.renderer.setMinimumBuildRadius(TILE_RADIUS_CHUNKS * 16);
    if (!this.hudWidget.isEnabled()) {
      this.renderer.tick();
    }

    if (this.resendTiles) {
      this.resendTiles = false;
      this.tileHashes.clear();
    }

    this.tickCounter++;
    this.streamTime += 50; // one game tick on the stream timeline
    boolean allowed = this.addon.isMinimapAllowed();

    if (this.tickCounter % Math.max(1, 20 / MinimapChannel.STATE_HZ) == 0) {
      stream.publishText(buildState(allowed));
    }

    if (!allowed) {
      this.tileRequests.clear();
    } else if (this.tickCounter % Math.max(1, 20 / MinimapChannel.TILE_HZ) == 0) {
      int sent = this.streamTiles(stream);
      this.streamRequestedTiles(stream, sent);
    }
  }

  // ---- state -------------------------------------------------------------------------------------

  private JsonObject buildState(boolean allowed) {
    Minecraft minecraft = Laby.labyAPI().minecraft();
    ClientPlayer player = minecraft.getClientPlayer();

    JsonObject state = new JsonObject();
    state.addProperty("t", MinimapChannel.MSG_STATE);
    // Tick-time stamp (see streamTime): the app's jitter-buffer playhead runs on THIS timeline,
    // so neither delivery jitter nor wall-clock tick scheduling can distort the motion.
    state.addProperty("ts", this.streamTime);
    state.addProperty("allowed", allowed);
    // Same sky brightness the HUD widget shades with, so both maps darken together at night.
    state.addProperty("day", num(this.renderer.dayTime()));
    state.addProperty("underground", this.renderer.isUnderground());

    if (player != null) {
      Position position = player.position();
      MinecraftCamera camera = minecraft.getCamera();
      state.addProperty("x", num(position.getX()));
      state.addProperty("y", num(position.getY()));
      state.addProperty("z", num(position.getZ()));
      state.addProperty("yaw", num(camera == null ? 0F : camera.getYaw()));
    }

    state.add("players", players(player));
    state.add("waypoints", waypoints());
    return state;
  }

  private JsonArray players(ClientPlayer self) {
    JsonArray players = new JsonArray();
    for (Player player : Laby.references().clientWorld().getPlayers()) {
      if (player == self) {
        continue;
      }
      Position position = player.position();
      JsonObject entry = new JsonObject();
      entry.addProperty("u", player.getUniqueId().toString());
      entry.addProperty("n", player.getName());
      entry.addProperty("x", num(position.getX()));
      entry.addProperty("z", num(position.getZ()));
      players.add(entry);
    }
    return players;
  }

  private JsonArray waypoints() {
    JsonArray waypoints = new JsonArray();
    try {
      for (var waypoint : Waypoints.references().waypointService().getVisible()) {
        DoubleVector3 position = waypoint.position();
        JsonObject entry = new JsonObject();
        entry.addProperty("x", num(position.getX()));
        entry.addProperty("z", num(position.getZ()));
        entry.addProperty("color", waypoint.meta().iconColor());
        waypoints.add(entry);
      }
    } catch (Throwable ignored) {
      // waypoints addon not present or not ready yet, skip silently
    }
    return waypoints;
  }

  // ---- tiles -------------------------------------------------------------------------------------

  /**
   * @return the number of tiles sent
   */
  private int streamTiles(ExternalDeviceStream stream) {
    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      return 0;
    }
    int centerChunkX = MathHelper.floor(player.position().getX()) >> 4;
    int centerChunkZ = MathHelper.floor(player.position().getZ()) >> 4;

    // Forget sent-hashes for chunks well outside the streaming radius: the app prunes distant
    // tiles from memory, so those areas MUST re-stream when the player returns; a permanent
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
      int hash = dataHash(data);
      Integer previous = this.tileHashes.get(key);
      if (previous != null && previous == hash) {
        continue;
      }

      this.tileHashes.put(key, hash);
      // Copy the planes out here: the renderer rebuilds ChunkData in place, so the encoder must
      // not read it once this tick is over.
      this.submitChunk(stream, key, ChunkSnapshot.of(data));
      if (++sent >= MAX_TILES_PER_TICK) {
        break;
      }
    }
    return sent;
  }

  /**
   * Sends saved tiles the app asked for with {@link MinimapChannel#CMD_TILES}, sharing the per pass
   * tile cap with the live tiles. Waits while a region is still loading from disk.
   */
  private void streamRequestedTiles(ExternalDeviceStream stream, int sent) {
    MapRegionStore store = this.worldMap.activeStore();
    if (store == null || !store.isIndexed()) {
      return;
    }

    TileRequest request;
    while (sent < MAX_TILES_PER_TICK && (request = this.tileRequests.peek()) != null) {
      if (request.cursor >= request.size()) {
        this.tileRequests.poll();
        continue;
      }

      int chunkX = request.chunkX();
      int chunkZ = request.chunkZ();
      int regionX = chunkX >> MapRegion.CHUNK_SHIFT;
      int regionZ = chunkZ >> MapRegion.CHUNK_SHIFT;
      MapRegion region = store.getRegion(regionX, regionZ);
      if (region == null && store.isLoading(regionX, regionZ)) {
        return;
      }

      request.cursor++;
      int localChunkX = chunkX & (MapRegion.CHUNKS - 1);
      int localChunkZ = chunkZ & (MapRegion.CHUNKS - 1);
      if (region == null || !region.hasChunk(localChunkX, localChunkZ)) {
        continue;
      }

      ChunkData data = region.chunk(localChunkX, localChunkZ);
      long key = chunkKey(chunkX, chunkZ);
      this.tileHashes.put(key, dataHash(data));
      this.submitChunk(stream, key, ChunkSnapshot.of(data));
      sent++;
    }
  }

  /** Runs on a network thread. */
  private JsonObject requestTiles(JsonObject request) {
    int minChunkX = request.get("minX").getAsInt();
    int minChunkZ = request.get("minZ").getAsInt();
    int width = request.get("maxX").getAsInt() - minChunkX + 1;
    int height = request.get("maxZ").getAsInt() - minChunkZ + 1;
    if (width <= 0 || height <= 0
        || width > MinimapChannel.MAX_REQUEST_CHUNKS
        || height > MinimapChannel.MAX_REQUEST_CHUNKS) {
      throw new IllegalArgumentException(
          "A tile request must span 1 to " + MinimapChannel.MAX_REQUEST_CHUNKS + " chunks per axis"
      );
    }

    this.tileRequests.add(new TileRequest(minChunkX, minChunkZ, width, height));
    while (this.tileRequests.size() > MAX_PENDING_REQUESTS) {
      this.tileRequests.poll();
    }

    JsonObject result = new JsonObject();
    result.addProperty("chunks", width * height);
    return result;
  }

  /**
   * Hands one chunk to the encoder thread: the colour PNG for devices that paint tiles as images,
   * and the raw planes for those that shade the map themselves. A device ignores the frame type it
   * does not understand. Whenever the chunk does not go out (encoder backed up, encoding failed)
   * its hash is dropped again, so the next pass retries the chunk.
   */
  private void submitChunk(ExternalDeviceStream stream, long key, ChunkSnapshot snapshot) {
    try {
      this.encoder.execute(() -> {
        byte[] tile = encodeTile(snapshot);
        if (tile == null) {
          this.tileHashes.remove(key);
          return;
        }
        stream.publishBinary(tile);
        stream.publishBinary(encodePlanes(snapshot));
      });
    } catch (RejectedExecutionException exception) {
      this.tileHashes.remove(key);
    }
  }

  /**
   * Colour, height and light planes of one chunk, see {@link MinimapChannel#BINARY_PLANES}. Runs on
   * the encoder thread.
   */
  private static byte[] encodePlanes(ChunkSnapshot snapshot) {
    ByteBuffer buffer = ByteBuffer.allocate(9 + 256 * (4 + 2 + 1));
    buffer.put(MinimapChannel.BINARY_PLANES);
    buffer.putInt(snapshot.chunkX());
    buffer.putInt(snapshot.chunkZ());
    for (int index = 0; index < 256; index++) {
      buffer.putInt(snapshot.colors()[index]);
    }
    for (int index = 0; index < 256; index++) {
      buffer.putShort(snapshot.heights()[index]);
    }
    for (int index = 0; index < 256; index++) {
      buffer.put(snapshot.lights()[index]);
    }
    return buffer.array();
  }

  /** Runs on the encoder thread. */
  private static byte[] encodeTile(ChunkSnapshot snapshot) {
    int chunkX = snapshot.chunkX();
    int chunkZ = snapshot.chunkZ();
    int[] colors = snapshot.colors();
    try {
      int size = 16 * TILE_SCALE;
      int[] pixels = new int[size * size];
      for (int z = 0; z < 16; z++) {
        for (int x = 0; x < 16; x++) {
          int color = colors[x * 16 + z];
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
      // Memory-cached on purpose: ImageIO.write(.., OutputStream) picks a
      // FileCacheImageOutputStream, which creates and writes a temp file for every single tile.
      try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(png)) {
        ImageIO.write(image, "png", output);
      }
      byte[] pngBytes = png.toByteArray();

      ByteArrayOutputStream out = new ByteArrayOutputStream(pngBytes.length + 9);
      DataOutputStream frame = new DataOutputStream(out);
      frame.writeByte(MinimapChannel.BINARY_TILE);
      frame.writeInt(chunkX);
      frame.writeInt(chunkZ);
      frame.write(pngBytes);
      return out.toByteArray();
    } catch (IOException exception) {
      return null;
    }
  }

  /** Change detection over everything a device may render: colour, height and light. */
  private static int dataHash(ChunkData data) {
    int hash = 1;
    for (int x = 0; x < 16; x++) {
      for (int z = 0; z < 16; z++) {
        hash = 31 * hash + data.getColor(x, z);
        hash = 31 * hash + data.getHeight(x, z);
        hash = 31 * hash + data.getLightLevel(x, z);
      }
    }
    return hash;
  }

  private static long chunkKey(int x, int z) {
    return (long) x << 32 | z & 0xFFFFFFFFL;
  }

  /** Two decimals are enough for a map on a phone and keep the state message small. */
  private static double num(double value) {
    return Math.round(value * 100.0D) / 100.0D;
  }

  /**
   * Snapshot of one chunk's planes, taken on the game thread: the renderer rebuilds
   * {@link ChunkData} in place, so the encoder thread must not read it.
   */
  private record ChunkSnapshot(int chunkX, int chunkZ, int[] colors, short[] heights, byte[] lights) {

    private static ChunkSnapshot of(ChunkData data) {
      int[] colors = new int[256];
      short[] heights = new short[256];
      byte[] lights = new byte[256];
      for (int x = 0; x < 16; x++) {
        for (int z = 0; z < 16; z++) {
          int index = x * 16 + z;
          colors[index] = data.getColor(x, z);
          int height = data.getHeight(x, z);
          heights[index] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, height));
          lights[index] = (byte) data.getLightLevel(x, z);
        }
      }
      return new ChunkSnapshot(data.getX(), data.getZ(), colors, heights, lights);
    }
  }

  private static final class TileRequest {

    private final int minChunkX;
    private final int minChunkZ;
    private final int width;
    private final int height;
    private int cursor;

    private TileRequest(int minChunkX, int minChunkZ, int width, int height) {
      this.minChunkX = minChunkX;
      this.minChunkZ = minChunkZ;
      this.width = width;
      this.height = height;
    }

    private int size() {
      return this.width * this.height;
    }

    private int chunkX() {
      return this.minChunkX + this.cursor % this.width;
    }

    private int chunkZ() {
      return this.minChunkZ + this.cursor / this.width;
    }
  }
}
