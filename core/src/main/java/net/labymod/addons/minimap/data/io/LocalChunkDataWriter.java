package net.labymod.addons.minimap.data.io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.labymod.addons.minimap.data.ChunkData;
import net.labymod.addons.minimap.data.LocalChunkData;
import net.labymod.addons.minimap.util.Compression;
import net.labymod.api.util.io.IOUtil;
import net.labymod.api.util.logging.Logging;
import org.jetbrains.annotations.NotNull;

public class LocalChunkDataWriter implements AutoCloseable {

  private static final Logging LOGGER = Logging.getLogger();
  private final Path destination;

  private LocalChunkDataWriter(Path destination) {
    this.destination = destination;
  }

  public static LocalChunkDataWriter open(Path destination) throws IOException {
    Files.createDirectories(destination.getParent());
    return new LocalChunkDataWriter(destination);
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

    Path chunksDirectory = this.destination.resolve("chunks");
    Path cachedChunkData = chunksDirectory.resolve(data.getX() + "x" + data.getZ() + ".dat");

    Files.createDirectories(chunksDirectory);
    Files.write(cachedChunkData, Compression.deflate(localChunkData.getRawBuffer()));
  }


  @Override
  public void close() throws Exception {
    zipDirectory(this.destination, this.destination.resolveSibling(this.destination.getFileName().toString() + ".zip"));
    Files.walkFileTree(this.destination, new DeleteVisitor());
  }

  // TODO (Christian) Move the zip & delete logic to labymod
  public static class DeleteVisitor extends SimpleFileVisitor<Path> {

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      IOUtil.delete(file);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
      IOUtil.delete(dir);
      return FileVisitResult.CONTINUE;
    }
  }

  public static void zipDirectory(Path directory, Path zipDestination) throws IOException {
    try (OutputStream outputStream = Files.newOutputStream(zipDestination);
        ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
      zipFiles(directory, directory.getFileName().toString(), zipOutputStream);
    }
  }

  private static void zipFiles(Path file, String fileName, ZipOutputStream outputStream)
      throws IOException {
    if (Files.isHidden(file)) {
      return;
    }

    if (Files.isDirectory(file)) {
      if (!fileName.endsWith("/")) {
        fileName += "/";
      }

      outputStream.putNextEntry(new ZipEntry(fileName));
      outputStream.closeEntry();

      try (Stream<@NotNull Path> files = Files.list(file)) {
        String finalFileName = fileName;
        files.forEach(child -> {
          try {
            zipFiles(child, finalFileName + child.getFileName(), outputStream);
          } catch (IOException exception) {
            LOGGER.error("Error zipping file", exception);
          }
        });
      }
    } else {
      try (InputStream stream = Files.newInputStream(file)) {
        ZipEntry entry = new ZipEntry(fileName);
        outputStream.putNextEntry(entry);
        stream.transferTo(outputStream);
        outputStream.closeEntry();
      }
    }

  }
}