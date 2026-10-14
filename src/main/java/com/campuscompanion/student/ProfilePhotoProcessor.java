package com.campuscompanion.student;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Checks an uploaded profile photo and makes the picture that is actually stored. It is plain Java
 * and uses only the image support of the JDK.
 *
 * <p>The upload is treated as hostile. It must be at most 2 MB, be declared as JPEG or PNG, start
 * with the bytes of that format, and open as a picture of a sensible size. The uploaded file itself
 * is then thrown away: a new 512 by 512 JPEG is drawn from its pixels and only that is kept. So
 * nothing hidden in the file survives, including the place and time a phone writes into its photos.
 */
@Component
public class ProfilePhotoProcessor {

  public static final long MAX_BYTES = 2L * 1024 * 1024;
  static final int MIN_SIDE = 64;
  static final int MAX_SIDE = 6000;
  static final int SIZE = 512;

  /** A picture is read with its shorter side at about this size, at least twice the stored size. */
  static final int READ_SIDE = 1024;

  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

  /** Returns the JPEG to store, or throws when the upload is not an acceptable photo. */
  public byte[] process(byte[] content, String declaredType) {
    if (content.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The photo is larger than 2 MB.");
    }
    boolean jpeg = "image/jpeg".equalsIgnoreCase(declaredType) && startsWith(content, JPEG);
    boolean png = "image/png".equalsIgnoreCase(declaredType) && startsWith(content, PNG);
    if (!jpeg && !png) {
      throw invalid();
    }
    BufferedImage square = squareOf(decode(content, jpeg ? "jpeg" : "png"));
    // A phone often stores a photo on its side and notes how to turn it. That note is not kept,
    // so the picture is turned upright here.
    return encode(turned(square, jpeg ? orientationOf(content) : 1));
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
      if (Math.min(width, height) < MIN_SIDE || Math.max(width, height) > MAX_SIDE) {
        throw invalid();
      }
      // Only 512 pixels are kept, so a large picture is read at a lower resolution straight away:
      // every second, third or fourth pixel. A 6000 by 6000 picture would otherwise take about
      // 144 MB of memory once decoded.
      ImageReadParam every = reader.getDefaultReadParam();
      int step = Math.max(1, Math.min(width, height) / READ_SIDE);
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

  /** The middle square of the picture, drawn at 512 by 512 on white. */
  private static BufferedImage squareOf(BufferedImage image) {
    int side = Math.min(image.getWidth(), image.getHeight());
    int left = (image.getWidth() - side) / 2;
    int top = (image.getHeight() - side) / 2;
    BufferedImage square = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = square.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, SIZE, SIZE);
    graphics.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    graphics.drawImage(image, 0, 0, SIZE, SIZE, left, top, left + side, top + side, null);
    graphics.dispose();
    return square;
  }

  /**
   * Turns a square picture upright. Orientation 6, 3 and 8 mean one, two and three quarter turns.
   */
  static BufferedImage turned(BufferedImage square, int orientation) {
    int quarterTurns =
        switch (orientation) {
          case 6 -> 1;
          case 3 -> 2;
          case 8 -> 3;
          default -> 0;
        };
    if (quarterTurns == 0) {
      return square;
    }
    int size = square.getWidth();
    BufferedImage upright = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = upright.createGraphics();
    graphics.rotate(Math.PI / 2 * quarterTurns, size / 2.0, size / 2.0);
    graphics.drawImage(square, 0, 0, null);
    graphics.dispose();
    return upright;
  }

  private static byte[] encode(BufferedImage image) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!ImageIO.write(image, "jpg", out)) {
        throw new IllegalStateException("The JDK has no JPEG writer");
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("The photo could not be written", e);
    }
  }

  /**
   * How a JPEG says it should be turned: the value of its EXIF orientation tag, from 1 to 8, or 1
   * when it has none. The bytes come from a stranger, so every position is checked before it is
   * read, and anything unexpected simply means "no turn".
   */
  static int orientationOf(byte[] jpeg) {
    int position = 2;
    while (position + 4 <= jpeg.length && (jpeg[position] & 0xFF) == 0xFF) {
      int marker = jpeg[position + 1] & 0xFF;
      int length = ((jpeg[position + 2] & 0xFF) << 8) | (jpeg[position + 3] & 0xFF);
      if (marker == 0xDA || length < 2) {
        break;
      }
      int end = position + 2 + length;
      if (marker == 0xE1 && end <= jpeg.length && isExif(jpeg, position + 4)) {
        return orientationIn(jpeg, position + 10, end);
      }
      position = end;
    }
    return 1;
  }

  private static boolean isExif(byte[] data, int at) {
    return at + 6 <= data.length
        && data[at] == 'E'
        && data[at + 1] == 'x'
        && data[at + 2] == 'i'
        && data[at + 3] == 'f'
        && data[at + 4] == 0
        && data[at + 5] == 0;
  }

  /** Looks for tag 0x0112 in the first directory of the EXIF block between start and end. */
  private static int orientationIn(byte[] data, int start, int end) {
    if (start + 8 > end) {
      return 1;
    }
    boolean littleEndian = data[start] == 'I' && data[start + 1] == 'I';
    boolean bigEndian = data[start] == 'M' && data[start + 1] == 'M';
    if (!littleEndian && !bigEndian) {
      return 1;
    }
    long offset = number(data, start + 4, 4, littleEndian);
    if (offset < 8 || offset > end - start - 2) {
      return 1;
    }
    int directory = start + (int) offset;
    int entries = (int) number(data, directory, 2, littleEndian);
    for (int i = 0; i < entries; i++) {
      int entry = directory + 2 + 12 * i;
      if (entry + 12 > end) {
        break;
      }
      if (number(data, entry, 2, littleEndian) == 0x0112) {
        long value = number(data, entry + 8, 2, littleEndian);
        return value >= 1 && value <= 8 ? (int) value : 1;
      }
    }
    return 1;
  }

  private static long number(byte[] data, int at, int bytes, boolean littleEndian) {
    long value = 0;
    for (int i = 0; i < bytes; i++) {
      int next = data[littleEndian ? at + bytes - 1 - i : at + i] & 0xFF;
      value = (value << 8) | next;
    }
    return value;
  }

  private static boolean startsWith(byte[] content, byte[] signature) {
    if (content.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (content[i] != signature[i]) {
        return false;
      }
    }
    return true;
  }

  private static ApiException invalid() {
    return new ApiException(
        ErrorCode.UPLOAD_INVALID_TYPE, "Use a JPEG or PNG photo of at least 64 by 64 pixels.");
  }
}
