package net.labymod.addons.minimap.data;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.labymod.addons.minimap.api.util.Util;
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
import net.labymod.api.util.math.position.Position;
import net.labymod.api.util.math.vector.IntVector3;

public class ChunkDataStorage {

  private static final Logging LOGGER = Logging.getLogger();
  private static final long BITS = 32L;
  private static final long MASK = 0xFFFFFFFFL;
  private final Map<Long, ChunkData> chunks = new HashMap<>();
  private final CompilationService compilationService = new CompilationService();
  private boolean shouldProcess;

  public ChunkDataStorage() {
  }

  @Subscribe
  public void onChunk(ChunkEvent event) {
    Type type = event.getType();
    if (type == Type.LOAD) {
      this.loadChunk(event.getChunk());
    } else if (type == Type.UNLOAD) {
      this.unloadChunk(event.getChunk());
    }
  }

  @Subscribe
  public void onServerSwitch(ServerSwitchEvent event) {
    this.clearAll();
  }

  @Subscribe
  public void onServerDisconnect(ServerDisconnectEvent event) {
    this.clearAll();
  }

  private void writeSavedData(Map<Long, ChunkData> chunkMap) {
    Collection<ChunkData> chunks = chunkMap.values();
    int minX = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;

    for (ChunkData chunk : chunks) {
      if (chunk.getX() < minX) {
        minX = chunk.getX();
      }

      if (chunk.getX() > maxX) {
        maxX = chunk.getX();
      }

      if (chunk.getZ() < minZ) {
        minZ = chunk.getZ();
      }

      if (chunk.getZ() > maxZ) {
        maxZ = chunk.getZ();
      }
    }

    int width = Math.abs(maxX - minX) * 16;
    int height = Math.abs(maxZ - minZ) * 16;

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    for (int x = minX; x < maxX; x++) {
      for (int z = minZ; z < maxZ; z++) {
        long id = Util.getChunkId(x, z);
        ChunkData chunkData = chunkMap.get(id);
        if (chunkData != null) {
          int xPos = (x - minX) * 16;
          int yPos = (z - minZ) * 16;

          for (int chunkX = 0; chunkX < 16; chunkX++) {
            for (int chunkZ = 0; chunkZ < 16; chunkZ++) {
              image.setRGB(xPos + chunkX, yPos + chunkZ, chunkData.getColor(chunkX, chunkZ));
            }
          }
        }
      }
    }

    try {
      ImageIO.write(image, "png", new File("saved_data.png"));
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
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

  private void clearAll() {
    this.chunks.clear();
    this.setShouldProcess(true);
  }

  public boolean isChunkLoaded(int x, int z) {
    return this.chunks.containsKey(this.getChunkId(x, z));
  }

  public void setPlayerPosition(Position position, boolean underground) {
    this.compilationService.setPlayerPosition(position, underground);
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
    if (this.compilationService.compile(data)) {
      //this.writer.write(data);
    }
  }

  public void resetCompilations() {
    this.compilationService.resetCompilations();
  }

}
