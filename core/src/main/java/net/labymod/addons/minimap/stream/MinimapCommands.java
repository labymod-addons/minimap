package net.labymod.addons.minimap.stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.labymod.addons.waypoints.Waypoints;
import net.labymod.addons.waypoints.waypoint.WaypointBuilder;
import net.labymod.addons.waypoints.waypoint.WaypointIcon;
import net.labymod.addons.waypoints.waypoint.WaypointMeta;
import net.labymod.addons.waypoints.waypoint.WaypointType;
import net.labymod.api.Laby;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.externaldevice.ExternalDeviceCommandException;
import net.labymod.api.externaldevice.ExternalDeviceRegistration;
import net.labymod.api.util.Color;
import net.labymod.api.util.math.position.Position;
import net.labymod.api.util.math.vector.DoubleVector3;
import org.jetbrains.annotations.Nullable;

/**
 * The operations the minimap adds to the command set of paired controllers (a Stream Deck, the
 * Laby app), on top of the state and tiles {@link MinimapPublisher} streams.
 *
 * <ul>
 *   <li>{@value #OP_WAYPOINT} (+ {@code name}, {@code color} as an RGB number, {@code type}:
 *       {@code permanent} or {@code session}): puts a waypoint where the player stands and
 *       answers with its name and coordinates.</li>
 * </ul>
 *
 * <p>The waypoint itself belongs to the waypoints addon, which the minimap already reads for its
 * overlay; when it is not installed the command answers {@code waypoints_missing} instead of
 * failing silently, so a key can say so.
 */
public final class MinimapCommands {

  public static final String OP_WAYPOINT = "minimap.waypoint";

  /** Same blue the app and the plugin offer as the default. */
  private static final int DEFAULT_COLOR = 0x4AC0FF;
  /** The waypoint is created on the render thread; a controller waits for the answer. */
  private static final long TIMEOUT_SECONDS = 3L;

  private static ExternalDeviceRegistration registration;

  private MinimapCommands() {
  }

  /** Offers the operation to controllers; dropped again while the addon is switched off. */
  public static void register() {
    unregister();
    registration = Laby.references().externalDeviceService().control()
        .registerCommand(OP_WAYPOINT, MinimapCommands::waypoint);
  }

  public static void unregister() {
    if (registration != null) {
      registration.close();
      registration = null;
    }
  }

  private static JsonObject waypoint(JsonObject request) throws Exception {
    ClientPlayer player = Laby.labyAPI().minecraft().getClientPlayer();
    if (player == null) {
      throw new ExternalDeviceCommandException("no_world");
    }
    Position position = player.position();
    DoubleVector3 location = new DoubleVector3(position.getX(), position.getY(), position.getZ());

    String name = string(request, "name");
    if (name == null || name.isBlank()) {
      // A nameless waypoint in the list tells nobody anything; its coordinates do.
      name = String.format(Locale.ROOT, "%.0f, %.0f, %.0f",
          location.getX(), location.getY(), location.getZ());
    }
    Integer color = integer(request, "color");
    boolean session = "session".equals(string(request, "type"));

    String title = name;
    CompletableFuture<Void> created = new CompletableFuture<>();
    Laby.labyAPI().minecraft().executeOnRenderThread(() -> {
      try {
        WaypointMeta meta = WaypointBuilder.create()
            .identifierPrefix(title)
            .title(Component.text(title))
            .color(Color.of(color == null ? DEFAULT_COLOR : color, 255))
            .icon(WaypointIcon.DEFAULT)
            .type(session ? WaypointType.SERVER_SESSION : WaypointType.PERMANENT)
            .location(location)
            .visible(true)
            .applyCurrentContext()
            .currentDimension()
            .build();
        Waypoints.references().waypointService().addWaypoint(meta);
        // add() only stores and saves; the overlays rebuild their lists on the refresh event.
        Waypoints.refresh();
        created.complete(null);
      } catch (Throwable throwable) {
        created.completeExceptionally(throwable);
      }
    });

    try {
      created.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (Exception exception) {
      Throwable cause = exception.getCause() == null ? exception : exception.getCause();
      // No waypoints addon at all: its classes are missing, or its service is not up yet.
      if (cause instanceof LinkageError || cause instanceof NullPointerException) {
        throw new ExternalDeviceCommandException("waypoints_missing");
      }
      throw exception;
    }

    JsonObject answer = new JsonObject();
    answer.addProperty("name", title);
    answer.addProperty("x", Math.round(location.getX()));
    answer.addProperty("y", Math.round(location.getY()));
    answer.addProperty("z", Math.round(location.getZ()));
    return answer;
  }

  @Nullable
  private static String string(JsonObject request, String key) {
    JsonElement element = request.get(key);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
  }

  @Nullable
  private static Integer integer(JsonObject request, String key) {
    JsonElement element = request.get(key);
    return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()
        ? element.getAsInt() : null;
  }
}
