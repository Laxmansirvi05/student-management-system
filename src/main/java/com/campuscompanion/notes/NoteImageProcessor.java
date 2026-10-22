package com.campuscompanion.notes;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.image.ExifOrientation;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Makes the picture that is stored for an image in a note. The upload is treated as hostile: its
 * size is read before it is decoded, and the uploaded file itself is thrown away. A new JPEG is
 * drawn from its pixels on white, turned upright by its EXIF tag, so nothing hidden in the file
 * survives, including the place and time a phone writes into its photos. JPEG is what the private
 * storage bucket accepts for pictures.
 */
@Component
public class NoteImageProcessor {

  static final int MAX_SIDE = 8000;
  static final long MAX_PIXELS = 40_000_000L;
  static final int OUTPUT_SIDE = 4096;
  static final float QUALITY = 0.88f;

  /** Returns the JPEG to store for a JPEG, PNG or GIF picture. A GIF keeps its first frame. */
  public byte[] toJpeg(byte[] content, String format) {
    BufferedImage image = decode(content, format);
    int orientation = "jpeg".equals(format) ? ExifOrientation.of(content) : 1;
    return encode(upright(fitted(image), orientation));
  }

  /** Opens the picture. Its size is read first, so a huge picture is refused before decoding. */
  private static BufferedImage decode(byte[] content, String format) {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
    if (!readers.hasNext()) {
      throw new IllegalStateException("The JDK has no reader for " + format);
    }
    ImageReader reader = readers.next();
    try (ImageInputStream input =
        ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
      reader.setInput(input, true, true);
      int width = reader.getWidth(0);
      int height = reader.getHeight(0);
      if (width < 1
          || height < 1
          || Math.max(width, height) > MAX_SIDE
          || (long) width * height > MAX_PIXELS) {
        throw invalid();
      }
      // Only 4096 pixels are kept on the long side, so a large picture is read at a lower
      // resolution straight away: every second, third or fourth pixel.
      ImageReadParam every = reader.getDefaultReadParam();
      int step = Math.max(1, Math.max(width, height) / OUTPUT_SIDE);
      every.setSourceSubsampling(step, step, 0, 0);
      BufferedImage image = reader.read(0, every);
      if (image == null) {
        throw invalid();
      }
      return image;
    } catch (IOException | RuntimeException notAPicture) {
      throw notAPicture instanceof ApiException refused ? refused : invalid();
    } finally {
      reader.dispose();
    }
  }

  /** The picture on white, no larger than 4096 pixels on its long side. */
  private static BufferedImage fitted(BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    double scale = Math.min(1.0, (double) OUTPUT_SIDE / Math.max(width, height));
    int targetWidth = Math.max(1, (int) Math.round(width * scale));
    int targetHeight = Math.max(1, (int) Math.round(height * scale));
    BufferedImage flat = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = flat.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, targetWidth, targetHeight);
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
    graphics.dispose();
    return flat;
  }

  /** Turns the picture upright. Orientation 3, 6 and 8 are a half turn and quarter turns. */
  static BufferedImage upright(BufferedImage image, int orientation) {
    int width = image.getWidth();
    int height = image.getHeight();
    AffineTransform transform = new AffineTransform();
    boolean swap = false;
    switch (orientation) {
      case 2 -> {
        transform.scale(-1, 1);
        transform.translate(-width, 0);
      }
      case 3 -> {
        transform.translate(width, height);
        transform.rotate(Math.PI);
      }
      case 4 -> {
        transform.scale(1, -1);
        transform.translate(0, -height);
      }
      case 6 -> {
        transform.translate(height, 0);
        transform.rotate(Math.PI / 2);
        swap = true;
      }
      case 8 -> {
        transform.translate(0, width);
        transform.rotate(-Math.PI / 2);
        swap = true;
      }
      default -> {
        return image;
      }
    }
    BufferedImage turned =
        new BufferedImage(swap ? height : width, swap ? width : height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = turned.createGraphics();
    graphics.drawImage(image, transform, null);
    graphics.dispose();
    return turned;
  }

  private static byte[] encode(BufferedImage image) {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try {
      ImageWriteParam parameters = writer.getDefaultWriteParam();
      parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      parameters.setCompressionQuality(QUALITY);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
        writer.setOutput(stream);
        writer.write(null, new IIOImage(image, null, null), parameters);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("The picture could not be written", e);
    } finally {
      writer.dispose();
    }
  }

  private static ApiException invalid() {
    return new ApiException(
        ErrorCode.UPLOAD_INVALID_TYPE,
        "That picture could not be read. Use a JPEG, PNG or GIF file.");
  }
}
