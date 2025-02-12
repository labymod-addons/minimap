package net.labymod.addons.minimap.data.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipOutputStream;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.LocalChunkData;

public class LocalChunkDataWriter implements AutoCloseable {

  private final ZipOutputStream outputStream;

  private LocalChunkDataWriter(ZipOutputStream outputStream) {
    this.outputStream = outputStream;
  }

  public static LocalChunkDataWriter open(Path destination) throws IOException {
    Files.createDirectories(destination.getParent());
    return new LocalChunkDataWriter(new ZipOutputStream(Files.newOutputStream(destination,
        StandardOpenOption.APPEND)));
  }

  public void write(ChunkData data) throws IOException {
    LocalChunkData localChunkData = new LocalChunkData();

    localChunkData.setPosition(data.getX(), data.getZ());
    for (int x = 0; x < ChunkData.CHUNK_SIZE; x++) {
      for (int z = 0; z < ChunkData.CHUNK_SIZE; z++) {
        localChunkData.setColor(x, z, data.getColor(x, z));
        localChunkData.setHeight(x, z, data.getHeight(x, z));
        localChunkData.setLightLevel(x, z, data.getLightLevel(x, z));
      }
    }

    // TODO (Christian) Write chunk data to the disk
  }


  @Override
  public void close() throws Exception {
    this.outputStream.close();
  }
}