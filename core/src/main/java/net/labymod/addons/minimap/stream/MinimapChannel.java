package net.labymod.addons.minimap.stream;

/**
 * The minimap's channel on top of the Phone HUD stream (see
 * {@code net.labymod.api.externaldevice.ExternalDeviceService}: the core owns transport, pairing and the
 * generic widget snapshot; this addon only publishes its own frames):
 *
 * <ul>
 *   <li><b>Text</b> {@value #MSG_STATE} ({@value #STATE_HZ}&nbsp;Hz): {@code {"t":"state",
 *       "allowed":true,"x":..,"y":..,"z":..,"yaw":..,"players":[{"u":"..","n":"..","x":..,
 *       "z":..}],"waypoints":[{"n":"..","x":..,"z":..,"color":..}]}}. {@code allowed} is
 *       {@code false} on blacklisted servers, the app greys the map out and tiles stop.</li>
 *   <li><b>Binary</b> tile frames ({@value #TILE_HZ}&nbsp;Hz, delta-only): one 16&times;16-block
 *       chunk as {@code [1 byte type={@value #BINARY_TILE}][int32 chunkX][int32 chunkZ][PNG
 *       bytes]} (big-endian). The PNG is nearest-neighbour upscaled (see
 *       {@code MinimapPublisher#TILE_SCALE}) so the phone gets crisp block edges; its pixel size
 *       is therefore a multiple of 16. Chunk world bounds are
 *       {@code [chunkX*16 .. chunkX*16+15]}.</li>
 *   <li><b>Binary</b> plane frames (sent with every tile frame, live or requested): the raw
 *       per-block data the HUD widget's shader works from, so a device can shade the map itself
 *       (slope relief, day/night, block light) instead of showing flat colours. Layout, big-endian,
 *       block index {@code x * 16 + z}: {@code [1 byte type={@value #BINARY_PLANES}][int32
 *       chunkX][int32 chunkZ][256 &times; int32 ARGB colour][256 &times; int16 block Y][256 &times;
 *       uint8 light (sky &lt;&lt; 4 | block)]}. Devices that only know the PNG frame ignore this
 *       type.</li>
 *   <li><b>Command</b> {@value #CMD_TILES}: {@code {"minX":..,"minZ":..,"maxX":..,"maxZ":..}}
 *       in chunk coordinates, at most {@value #MAX_REQUEST_CHUNKS} chunks per axis. Streams the
 *       saved tiles of that area as tile frames, so the app can show explored areas away from the
 *       player. Answers {@code {"chunks":n}}, the number of chunks looked up. The addon keeps only
 *       the newest four requests.</li>
 * </ul>
 *
 * <p>The state additionally carries {@code day} (sky brightness 0..1 as the HUD shader uses it, 1
 * while the cave view is active) and {@code underground} (cave view on).
 */
public final class MinimapChannel {

  public static final String MSG_STATE = "state";
  public static final byte BINARY_TILE = 0x01;
  public static final byte BINARY_PLANES = 0x02;
  public static final String CMD_TILES = "minimap.tiles";
  public static final int MAX_REQUEST_CHUNKS = 64;

  /** Player position/heading + overlays, in Hz (20 = every game tick). */
  public static final int STATE_HZ = 20;
  /** Changed map tiles, in Hz; terrain changes slowly. */
  public static final int TILE_HZ = 5;

  private MinimapChannel() {
  }
}
