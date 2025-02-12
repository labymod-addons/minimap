package net.labymod.addons.minimap.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class Compression {

  private static final int BUFFER_SIZE = 1024;

  private Compression() {
  }

  public static byte[] deflate(byte[] data) {
    Deflater deflater = new Deflater();
    deflater.setLevel(1);
    deflater.setInput(data);
    deflater.finish();

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
      byte[] buffer = new byte[BUFFER_SIZE];

      while (!deflater.finished()) {
        int length = deflater.deflate(buffer);
        outputStream.write(buffer, 0, length);
      }
      deflater.end();

      return outputStream.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  public static byte[] inflate(byte[] data) throws DataFormatException {
    Inflater inflater = new Inflater();
    inflater.setInput(data);

    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length)) {
      byte[] buffer = new byte[BUFFER_SIZE];

      while (!inflater.finished()) {
        int length = inflater.inflate(buffer);
        outputStream.write(buffer, 0, length);
      }

      inflater.end();

      return outputStream.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

}
