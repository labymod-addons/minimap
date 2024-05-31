package net.labymod.addons.minimap.map.v2;

import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.network.server.ServerSwitchEvent;
import net.labymod.api.event.client.world.chunk.BlockUpdateEvent;
import net.labymod.api.event.client.world.chunk.BlockUpdateEvent.Flags;
import net.labymod.api.event.client.world.chunk.ChunkEvent;
import net.labymod.api.event.client.world.chunk.ChunkEvent.Type;
import net.labymod.api.util.logging.Logging;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class MinimapChunkStorage {

  private static final Logging LOGGER = Logging.getLogger();
  private static final long BITS = 32L;
  private static final long MASK = 0xFFFFFFFFL;
  private final Map<Long, MinimapChunk> chunks = new HashMap<>();
  private boolean shouldProcess;

  @Subscribe
  public void onChunk(ChunkEvent event) {
    Type type = event.getType();
    if (type == Type.LOAD) {
      this.loadChunk(event.getChunk());
    } else if (type == Type.UNLOAD) {
     // this.unloadChunk(event.getChunk());
    }
  }

  @Subscribe
  public void onServerSwitch(ServerSwitchEvent event) {
    this.chunks.clear();
  }

  @Subscribe
  public void onServerDisconnect(ServerDisconnectEvent event) {
    this.chunks.clear();
  }

  @Subscribe
  public void onBlockUpdate(BlockUpdateEvent event) {
    MinimapChunk minimapChunk = this.chunks.get(this.getChunkId(event.getChunk()));
    if (minimapChunk != null) {
      minimapChunk.resetCompilation();
      this.setShouldProcess(true);
    }
  }

  public boolean isChunkLoaded(int x, int z) {
    return this.chunks.containsKey(this.getChunkId(x, z));
  }

  public boolean shouldProcess() {
    return this.shouldProcess;
  }

  public void processed() {
    this.setShouldProcess(false);
  }

  public MinimapChunk getChunk(int x, int z) {
    return this.chunks.get(this.getChunkId(x, z));
  }

  private void loadChunk(Chunk chunk) {
    this.chunks.put(this.getChunkId(chunk), new MinimapChunk(chunk));
    this.setShouldProcess(true);
  }

  private void unloadChunk(Chunk chunk) {
    this.chunks.remove(this.getChunkId(chunk));
    this.setShouldProcess(true);
  }

  public void setShouldProcess(boolean shouldProcess) {
    this.shouldProcess = shouldProcess;
  }

  public int getMinX() {
    int x = Integer.MAX_VALUE;
    for (MinimapChunk chunk : this.getChunks()) {
      if (chunk.getX() < x) {
        x = chunk.getX();
      }
    }

    return x;
  }

  public int getMinZ() {
    int z = Integer.MAX_VALUE;
    for (MinimapChunk chunk : this.getChunks()) {
      if (chunk.getZ() < z) {
        z = chunk.getZ();
      }
    }

    return z;
  }

  public int getMaxX() {
    int x = Integer.MIN_VALUE;
    for (MinimapChunk chunk : this.getChunks()) {
      if (chunk.getX() > x) {
        x = chunk.getX();
      }
    }

    return x;
  }

  public int getMaxZ() {
    int z = Integer.MIN_VALUE;
    for (MinimapChunk chunk : this.getChunks()) {
      if (chunk.getZ() > z) {
        z = chunk.getZ();
      }
    }

    return z;
  }

  public Collection<MinimapChunk> getChunks() {
    return this.chunks.values();
  }

  private long getChunkId(Chunk chunk) {
    return this.getChunkId(chunk.getChunkX(), chunk.getChunkZ());
  }

  private long getChunkId(int chunkX, int chunkZ) {
    return chunkX & MASK | (chunkZ & MASK) << BITS;
  }

}
