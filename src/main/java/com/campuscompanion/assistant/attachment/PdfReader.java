package com.campuscompanion.assistant.attachment;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Reads a PDF the way a careful person would: the text of each page, in reading order, from the
 * text layer; and a picture of every page whose text layer cannot be trusted. A page is shown as a
 * picture when it has almost no text (a scan or a photograph of paper), when its text is not real
 * text (a font the file does not explain, which comes out as symbols), or when it holds a large
 * picture (a diagram or a figure), so the model can read what the text cannot say. Mathematics set
 * with special fonts and handwriting are read from the picture, not the text.
 *
 * <p>A PDF is treated as hostile: pages are counted before they are read, at most {@link
 * #MAX_PAGES} are read and {@link #MAX_PICTURES} are drawn, a drawn page is at most 1,568 pixels on
 * its long side, and the whole reading runs on a thread of its own under a time limit.
 */
@Component
public class PdfReader {

  public static final int MAX_PAGES = 60;
  public static final int MAX_PICTURES = 8;
  static final int MAX_CHARS = 60_000;
  static final int PICTURE_SIDE = 1568;
  static final int MIN_TEXT_CHARS = 40;
  static final int LARGE_IMAGE_PIXELS = 250;
  static final Duration TIME_LIMIT = Duration.ofSeconds(45);

  /** Two at a time: drawing pages needs memory, and a small host has little of it. */
  private static final ExecutorService WORKERS =
      Executors.newFixedThreadPool(
          2,
          task -> {
            Thread thread = new Thread(task, "pdf-reader");
            thread.setDaemon(true);
            return thread;
          });

  /**
   * One page: its number (from 1), its text, and a JPEG of the page when it needs one (else null).
   */
  public record Page(int number, String text, byte[] picture) {}

  /**
   * @param pages how many pages the PDF has
   * @param read the pages that were read, in order
   * @param textCut true when reading stopped early because the text got too long
   * @param unseen pages that need a picture but did not get one (the limit was reached)
   */
  public record Content(int pages, List<Page> read, boolean textCut, int unseen) {}

  /** Reads the PDF, drawing pages only when {@code pictures} is true. */
  public Content read(byte[] file, boolean pictures) {
    Future<Content> job = WORKERS.submit(() -> readNow(file, pictures));
    try {
      return job.get(TIME_LIMIT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException tooSlow) {
      job.cancel(true);
      throw new ApiException(
          ErrorCode.UPLOAD_TOO_LARGE, "This PDF took too long to read. Try a smaller file.");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw unreadable();
    } catch (ExecutionException failed) {
      if (failed.getCause() instanceof ApiException api) {
        throw api;
      }
      throw unreadable();
    }
  }

  private static Content readNow(byte[] file, boolean pictures) {
    try (PDDocument document = Loader.loadPDF(file)) {
      int total = document.getNumberOfPages();
      if (total < 1) {
        throw unreadable();
      }
      PDFTextStripper stripper = new PDFTextStripper();
      stripper.setSortByPosition(true);
      PDFRenderer renderer = new PDFRenderer(document);
      renderer.setSubsamplingAllowed(true);
      List<Page> read = new ArrayList<>();
      int characters = 0;
      int drawn = 0;
      int unseen = 0;
      boolean cut = false;
      for (int index = 0; index < Math.min(total, MAX_PAGES); index++) {
        stripper.setStartPage(index + 1);
        stripper.setEndPage(index + 1);
        String text = tidy(stripper.getText(document));
        if (characters + text.length() > MAX_CHARS) {
          cut = true;
          break;
        }
        characters += text.length();
        byte[] picture = null;
        if (needsPicture(document.getPage(index), text)) {
          if (pictures && drawn < MAX_PICTURES) {
            picture = draw(renderer, document.getPage(index), index);
            drawn++;
          } else {
            unseen++;
          }
        }
        read.add(new Page(index + 1, text, picture));
      }
      return new Content(total, read, cut, unseen);
    } catch (InvalidPasswordException locked) {
      throw new ApiException(
          ErrorCode.UPLOAD_INVALID_TYPE,
          "This PDF is password protected. Remove the password and attach it again.");
    } catch (IOException | RuntimeException broken) {
      if (broken instanceof ApiException api) {
        throw api;
      }
      throw unreadable();
    }
  }

  private static ApiException unreadable() {
    return new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, "That PDF could not be read.");
  }

  /** Text with its line breaks kept and its runs of blank lines and trailing spaces removed. */
  static String tidy(String text) {
    return text.replace("\r", "")
        .replaceAll("[ \\t]+\\n", "\n")
        .replaceAll("\\n{3,}", "\n\n")
        .strip();
  }

  /** Whether the text of this page cannot stand in for the page. */
  static boolean needsPicture(PDPage page, String text) {
    int letters = 0;
    int plain = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isLetterOrDigit(c)) {
        letters++;
      }
      if (Character.isLetterOrDigit(c)
          || Character.isWhitespace(c)
          || ".,;:!?()[]{}'\"-+=/%".indexOf(c) >= 0) {
        plain++;
      }
    }
    if (letters < MIN_TEXT_CHARS) {
      return true; // a scan, or a page that is only a drawing
    }
    if (plain < text.length() * 0.7) {
      return true; // a font the file does not explain: the text is symbols
    }
    return hasLargeImage(page);
  }

  private static boolean hasLargeImage(PDPage page) {
    PDResources resources = page.getResources();
    if (resources == null) {
      return false;
    }
    try {
      for (var name : resources.getXObjectNames()) {
        PDXObject object = resources.getXObject(name);
        if (object instanceof PDImageXObject image
            && image.getWidth() >= LARGE_IMAGE_PIXELS
            && image.getHeight() >= LARGE_IMAGE_PIXELS) {
          return true;
        }
      }
    } catch (IOException | RuntimeException unreadable) {
      return false;
    }
    return false;
  }

  private static byte[] draw(PDFRenderer renderer, PDPage page, int index) throws IOException {
    PDRectangle box = page.getCropBox();
    float longest = Math.max(box.getWidth(), box.getHeight());
    float scale = Math.min(2.0f, PICTURE_SIDE / Math.max(longest, 1f));
    BufferedImage image = renderer.renderImage(index, scale, ImageType.RGB);
    return jpeg(image);
  }

  static byte[] jpeg(BufferedImage image) throws IOException {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    ImageWriteParam parameters = writer.getDefaultWriteParam();
    parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
    parameters.setCompressionQuality(0.85f);
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (MemoryCacheImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
      writer.setOutput(output);
      writer.write(null, new IIOImage(image, null, null), parameters);
    } finally {
      writer.dispose();
    }
    return bytes.toByteArray();
  }
}
