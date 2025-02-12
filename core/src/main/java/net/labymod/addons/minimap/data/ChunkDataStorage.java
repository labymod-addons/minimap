package net.labymod.addons.minimap.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import net.labymod.addons.minimap.data.compilation.CompilationService;
import net.labymod.api.client.gui.screen.key.Key;
import net.labymod.api.client.world.chunk.Chunk;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.client.input.KeyEvent;
import net.labymod.api.event.client.input.KeyEvent.State;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.network.server.ServerSwitchEvent;
import net.labymod.api.event.client.world.chunk.BlockUpdateEvent;
import net.labymod.api.event.client.world.chunk.ChunkEvent;
import net.labymod.api.event.client.world.chunk.ChunkEvent.Type;
import net.labymod.api.event.client.world.chunk.LightUpdateEvent;
import net.labymod.api.util.logging.Logging;
import net.labymod.api.util.math.vector.IntVector3;

public class ChunkDataStorage {

  private static final Logging LOGGER = Logging.getLogger();
  private static final long BITS = 32L;
  private static final long MASK = 0xFFFFFFFFL;
  private final Map<Long, ChunkData> chunks = new HashMap<>();
  private final CompilationService compilationService = new CompilationService();
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
    ChunkData data = this.chunks.get(this.getChunkId(event.getChunk()));
    if (data != null) {
      this.resetCompilation(data);
      this.setShouldProcess(true);
    }
  }

  @Subscribe
  public void onLightUpdate(LightUpdateEvent event) {
    IntVector3 blockPosition = event.getBlockPosition();

    int x = blockPosition.getX();
    int z = blockPosition.getZ();

    int chunkX = x >> 4;
    int chunkZ = z >> 4;

    ChunkData chunk = this.chunks.get(this.getChunkId(chunkX, chunkZ));
    if (chunk != null) {
      this.resetCompilation(chunk);
      this.setShouldProcess(true);
    }
  }

  @Subscribe
  public void onKey(KeyEvent event) {
    if (event.state() == State.PRESS && event.key() == Key.O) {
      this.chunks.values().forEach(this::resetCompilation);
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

  public ChunkData getChunk(int x, int z) {
    return this.chunks.get(this.getChunkId(x, z));
  }

  private void loadChunk(Chunk chunk) {
    this.chunks.put(this.getChunkId(chunk), new GameChunkData(chunk));
    this.resetNeighboringChunks(chunk);
    this.setShouldProcess(true);
  }

  private void resetNeighboringChunks(Chunk currentChunk) {
    int chunkX = currentChunk.getChunkX();
    int chunkZ = currentChunk.getChunkZ();

    int minChunkX = currentChunk.getChunkX() - 1;
    int minChunkZ = currentChunk.getChunkZ() - 1;

    int maxChunkX = currentChunk.getChunkX() + 1;
    int maxChunkZ = currentChunk.getChunkZ() + 1;

    for (int x = minChunkX; x <= maxChunkX; x++) {
      for (int z = minChunkZ; z <= maxChunkZ; z++) {
        if (x == chunkX && z == chunkZ) {
          continue;
        }

        ChunkData chunk = this.getChunk(x, z);
        if (chunk != null) {
          this.resetCompilation(chunk);
        }
      }
    }
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
    for (ChunkData chunk : this.getChunks()) {
      if (chunk.getX() < x) {
        x = chunk.getX();
      }
    }

    return x;
  }

  public int getMinZ() {
    int z = Integer.MAX_VALUE;
    for (ChunkData chunk : this.getChunks()) {
      if (chunk.getZ() < z) {
        z = chunk.getZ();
      }
    }

    return z;
  }

  public int getMaxX() {
    int x = Integer.MIN_VALUE;
    for (ChunkData chunk : this.getChunks()) {
      if (chunk.getX() > x) {
        x = chunk.getX();
      }
    }

    return x;
  }

  public int getMaxZ() {
    int z = Integer.MIN_VALUE;
    for (ChunkData chunk : this.getChunks()) {
      if (chunk.getZ() > z) {
        z = chunk.getZ();
      }
    }

    return z;
  }

  public Collection<ChunkData> getChunks() {
    return this.chunks.values();
  }

  private long getChunkId(Chunk chunk) {
    return this.getChunkId(chunk.getChunkX(), chunk.getChunkZ());
  }

  private long getChunkId(int chunkX, int chunkZ) {
    return chunkX & MASK | (chunkZ & MASK) << BITS;
  }

  private void resetCompilation(ChunkData data) {
    this.compilationService.resetCompilation(data);
  }

  public void compile(ChunkData data) {
    this.compilationService.compile(data);
  }
}
