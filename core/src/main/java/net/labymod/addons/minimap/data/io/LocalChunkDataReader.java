package net.labymod.addons.minimap.data.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.labymod.addons.minimap.api.util.Util;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.LocalChunkData;
import net.labymod.addons.minimap.util.Compression;
import net.labymod.api.util.io.IOUtil;

public class LocalChunkDataReader implements AutoCloseable {

  private final ZipFile file;

  private LocalChunkDataReader(ZipFile file) {
    this.file = file;
  }

  public static LocalChunkDataReader open(Path file) throws IOException {
    return new LocalChunkDataReader(new ZipFile(file.toFile()));
  }

  public Map<Long, ChunkData> read(String worldName) {
    Enumeration<? extends ZipEntry> entries = this.file.entries();
    Map<Long, ChunkData> chunks = new HashMap<>();

    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      if (!name.startsWith(worldName) || !name.endsWith(".dat")) {
        continue;
      }

      String chunkName = name.substring(worldName.length() + 1 + "chunks/".length());
      chunkName = chunkName.substring(0, chunkName.length() - ".dat".length());

      String[] positions = chunkName.split("x", 2);
      int x = Integer.parseInt(positions[0]);
      int z = Integer.parseInt(positions[1]);

      long chunkId = Util.getChunkId(x, z);

      try (InputStream inputStream = this.file.getInputStream(entry)) {
        byte[] compressedData = IOUtil.readBytes(inputStream);
        byte[] uncompressedData = Compression.inflate(compressedData);

        chunks.put(chunkId, new LocalChunkData(uncompressedData));
      } catch (IOException | DataFormatException exception) {
        throw new IllegalStateException(exception);
      }
    }

    return chunks;
  }

  @Override
  public void close() throws Exception {
    if (this.file != null) {
      this.file.close();
    }
  }
}
