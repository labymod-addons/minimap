package net.labymod.addons.minimap.server;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.labymod.addons.minimap.server.MinimapPlayersPacket.Entry;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.network.server.SubServerSwitchEvent;
import net.labymod.api.event.client.world.WorldEnterEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Players the server reported with {@link MinimapPlayersPacket}. Servers send positions far less
 * often than every tick, so each player glides from where it is drawn to the new position over the
 * time since its previous one. Call every method on the render thread.
 */
public final class RemotePlayers {

  private static final long MIN_INTERVAL_NANOS = 50_000_000L;
  private static final long MAX_INTERVAL_NANOS = 5_000_000_000L;

  private final Map<UUID, RemotePlayer> players = new HashMap<>();

  public Collection<RemotePlayer> players() {
    return this.players.values();
  }

  @Nullable
  public RemotePlayer get(UUID uuid) {
    return this.players.get(uuid);
  }

  void update(List<Entry> entries) {
    long now = System.nanoTime();
    Map<UUID, Entry> received = new HashMap<>();
    for (Entry entry : entries) {
      received.put(entry.uuid(), entry);
      RemotePlayer player = this.players.get(entry.uuid());
      if (player == null || !player.dimension.equals(entry.dimension())) {
        this.players.put(entry.uuid(), new RemotePlayer(entry, now));
      } else {
        player.moveTo(entry, now);
      }
    }

    Iterator<UUID> iterator = this.players.keySet().iterator();
    while (iterator.hasNext()) {
      if (!received.containsKey(iterator.next())) {
        iterator.remove();
      }
    }
  }

  @Subscribe
  public void onWorldEnter(WorldEnterEvent event) {
    this.players.clear();
  }

  @Subscribe
  public void onSubServerSwitch(SubServerSwitchEvent event) {
    this.players.clear();
  }

  @Subscribe
  public void onServerDisconnect(ServerDisconnectEvent event) {
    this.players.clear();
  }

  public static final class RemotePlayer {

    private final UUID uuid;
    private final String dimension;
    private String name;
    private double fromX;
    private double fromZ;
    private double toX;
    private double toZ;
    private long startNanos;
    private long durationNanos = MIN_INTERVAL_NANOS;
    private long lastUpdateNanos;
    @Nullable
    private Icon head;

    private RemotePlayer(Entry entry, long now) {
      this.uuid = entry.uuid();
      this.dimension = entry.dimension();
      this.name = entry.name();
      this.fromX = entry.x();
      this.fromZ = entry.z();
      this.toX = entry.x();
      this.toZ = entry.z();
      this.startNanos = now;
      this.lastUpdateNanos = now;
    }

    public UUID uuid() {
      return this.uuid;
    }

    public String name() {
      return this.name;
    }

    public String dimension() {
      return this.dimension;
    }

    public double x(long now) {
      return this.fromX + (this.toX - this.fromX) * this.progress(now);
    }

    public double z(long now) {
      return this.fromZ + (this.toZ - this.fromZ) * this.progress(now);
    }

    public Icon head() {
      if (this.head == null) {
        this.head = Icon.head(this.uuid);
      }

      return this.head;
    }

    private void moveTo(Entry entry, long now) {
      this.fromX = this.x(now);
      this.fromZ = this.z(now);
      this.toX = entry.x();
      this.toZ = entry.z();
      this.name = entry.name();
      this.durationNanos = Math.max(MIN_INTERVAL_NANOS, Math.min(MAX_INTERVAL_NANOS, now - this.lastUpdateNanos));
      this.startNanos = now;
      this.lastUpdateNanos = now;
    }

    private double progress(long now) {
      return Math.min(1.0D, (now - this.startNanos) / (double) this.durationNanos);
    }
  }
}
