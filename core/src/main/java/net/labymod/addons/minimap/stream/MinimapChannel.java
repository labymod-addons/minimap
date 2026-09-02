package net.labymod.addons.minimap.stream;

/**
 * The minimap's channel on top of the Phone HUD stream (see
 * {@code net.labymod.api.externaldevice.ExternalDeviceService} — the core owns transport, pairing and the
 * generic widget snapshot; this addon only publishes its own frames):
 *
 * <ul>
 *   <li><b>Text</b> {@value #MSG_STATE} ({@value #STATE_HZ}&nbsp;Hz): {@code {"t":"state",
 *       "allowed":true,"x":..,"y":..,"z":..,"yaw":..,"players":[{"u":"..","n":"..","x":..,
 *       "z":..}],"waypoints":[{"x":..,"z":..,"color":..}]}}. {@code allowed} is {@code false} on
 *       blacklisted servers — the app greys the map out and tiles stop.</li>
 *   <li><b>Binary</b> tile frames ({@value #TILE_HZ}&nbsp;Hz, delta-only): one 16&times;16-block
 *       chunk as {@code [1 byte type={@value #BINARY_TILE}][int32 chunkX][int32 chunkZ][PNG
 *       bytes]} (big-endian). The PNG is nearest-neighbour upscaled (see
 *       {@code MinimapPublisher#TILE_SCALE}) so the phone gets crisp block edges; its pixel size
 *       is therefore a multiple of 16. Chunk world bounds are
 *       {@code [chunkX*16 .. chunkX*16+15]}.</li>
 * </ul>
 */
public final class MinimapChannel {

  public static final String MSG_STATE = "state";
  public static final byte BINARY_TILE = 0x01;

  /** Player position/heading + overlays, in Hz (20 = every game tick). */
  public static final int STATE_HZ = 20;
  /** Changed map tiles, in Hz — terrain changes slowly. */
  public static final int TILE_HZ = 5;

  private MinimapChannel() {
  }
}
