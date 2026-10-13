package com.campuscompanion.support;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Small pictures for upload tests, drawn in memory. None of them shows a person. */
public final class TestImages {

  private TestImages() {}

  /** A JPEG whose left half is red and whose right half is blue. */
  public static byte[] jpeg(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.RED);
    graphics.fillRect(0, 0, width / 2, height);
    graphics.setColor(Color.BLUE);
    graphics.fillRect(width / 2, 0, width - width / 2, height);
    graphics.dispose();
    return write(image, "jpg");
  }

  /** A PNG that is fully transparent except for a green square in the middle. */
  public static byte[] pngWithTransparency(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    graphics.setColor(Color.GREEN);
    graphics.fillRect(width / 4, height / 4, width / 2, height / 2);
    graphics.dispose();
    return write(image, "png");
  }

  public static byte[] gif(int width, int height) {
    return write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "gif");
  }

  /**
   * The same JPEG with an EXIF block put in after its first two bytes, the way a phone writes it.
   * The block holds the orientation and a text that stands for the place the photo was taken.
   */
  public static byte[] withExif(byte[] jpeg, int orientation, String hiddenText) {
    byte[] hidden = hiddenText.getBytes(StandardCharsets.US_ASCII);
    ByteArrayOutputStream payload = new ByteArrayOutputStream();
    payload.writeBytes(new byte[] {'E', 'x', 'i', 'f', 0, 0});
    // A big-endian TIFF header, then one directory with one entry: tag 0x0112, a short, one value.
    payload.writeBytes(new byte[] {'M', 'M', 0, 42, 0, 0, 0, 8});
    payload.writeBytes(new byte[] {0, 1});
    payload.writeBytes(new byte[] {0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0});
    payload.writeBytes(new byte[] {0, 0, 0, 0});
    payload.writeBytes(hidden);
    int length = payload.size() + 2;

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    out.writeBytes(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
    out.writeBytes(payload.toByteArray());
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  public static BufferedImage read(byte[] picture) {
    try {
      return ImageIO.read(new ByteArrayInputStream(picture));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static byte[] write(BufferedImage image, String format) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, format, out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
