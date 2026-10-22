package com.campuscompanion.notice;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.resource.PdfValidator;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Decides whether a file may be stored as a notice's official document, and returns what to store.
 *
 * <p>A PDF goes through the same checks as a learning resource and is stored as it is. A JPEG or
 * PNG picture (a photo or scan of the circular) is read and drawn again as a new JPEG, so nothing
 * but the pixels is kept; a very large picture is scaled down to {@value #MAX_STORED_SIDE} pixels
 * on its longer side, which still shows the stamp and signature clearly.
 */
@Component
public class NoticeDocumentChecker {

  public static final long MAX_BYTES = PdfValidator.MAX_BYTES;
  static final int MAX_SIDE = 12000;
  static final int MIN_SIDE = 200;
  static final int MAX_STORED_SIDE = 2400;
  private static final float JPEG_QUALITY = 0.88f;
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

  /** What is kept: the bytes and their type, application/pdf or image/jpeg. */
  public record Checked(byte[] content, String contentType) {}

  private final PdfValidator pdfValidator;

  public NoticeDocumentChecker(PdfValidator pdfValidator) {
    this.pdfValidator = pdfValidator;
  }

  public Checked check(byte[] content, String declaredType, String fileName) {
    if (content.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The file is larger than 10 MB.");
    }
    String type = declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT);
    if (type.equals("application/pdf")) {
      pdfValidator.validate(content, declaredType, fileName);
      return new Checked(content, "application/pdf");
    }
    boolean jpeg = type.equals("image/jpeg") && startsWith(content, JPEG);
    boolean png = type.equals("image/png") && startsWith(content, PNG);
    if (!jpeg && !png) {
      throw invalid();
    }
    return new Checked(redraw(read(content, jpeg ? "jpeg" : "png")), "image/jpeg");
  }

  private static BufferedImage read(byte[] content, String format) {
    try (ImageInputStream input =
        ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
      Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
      if (input == null || !readers.hasNext()) {
        throw invalid();
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);
        // The size is checked before the pixels are read, so a tiny file that claims a huge
        // picture cannot use up the memory.
        if (Math.min(width, height) < MIN_SIDE || Math.max(width, height) > MAX_SIDE) {
          throw invalid();
        }
        BufferedImage image = reader.read(0);
        if (image == null) {
          throw invalid();
        }
        return image;
      } finally {
        reader.dispose();
      }
    } catch (IOException | RuntimeException notAPicture) {
      throw notAPicture instanceof ApiException refused ? refused : invalid();
    }
  }

  private static byte[] redraw(BufferedImage source) {
    double scale =
        Math.min(1.0, (double) MAX_STORED_SIDE / Math.max(source.getWidth(), source.getHeight()));
    int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
    BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = out.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setColor(java.awt.Color.WHITE);
      graphics.fillRect(0, 0, width, height);
      graphics.drawImage(source, 0, 0, width, height, null);
    } finally {
      graphics.dispose();
    }
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
      writer.setOutput(output);
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(JPEG_QUALITY);
      writer.write(null, new IIOImage(out, null, null), param);
    } catch (IOException e) {
      throw new IllegalStateException("The picture could not be written", e);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
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
        ErrorCode.UPLOAD_INVALID_TYPE,
        "Use a PDF, or a JPEG or PNG picture of the notice of at least 200 by 200 pixels.");
  }
}
