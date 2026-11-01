package com.campuscompanion.assistant.attachment;

import com.campuscompanion.ai.provider.AiImage;
import com.campuscompanion.ai.provider.VisionProvider;
import com.campuscompanion.assistant.AssistantImageChecker;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns an attached file into what the model can use. It decides what a file is from its first
 * bytes, never from its name or the type the browser declared, and then:
 *
 * <ul>
 *   <li>a picture (JPEG or PNG) is checked and redrawn, and is read by a model that can see;
 *   <li>a PDF gives its text page by page, and a picture of each page that is a scan, has
 *       unreadable text or holds a figure ({@link PdfReader});
 *   <li>a Word document gives its headings, lists, tables and equations as text, and its pictures
 *       ({@link DocxReader}).
 * </ul>
 *
 * A file is only returned when something was really read from it. Otherwise the student gets an
 * error that says why, so a blank or unreadable file is never shown as ready.
 */
@Component
public class AttachmentReader {

  public static final long MAX_BYTES = 10L * 1024 * 1024;

  private final AssistantImageChecker images;
  private final PdfReader pdfs;
  private final DocxReader documents;
  private final VisionProvider vision;

  public AttachmentReader(
      AssistantImageChecker images, PdfReader pdfs, DocxReader documents, VisionProvider vision) {
    this.images = images;
    this.pdfs = pdfs;
    this.documents = documents;
    this.vision = vision;
  }

  /** Reads one file, or throws an error the student can read. */
  public ReadFile read(String fileName, byte[] file) {
    if (file == null || file.length == 0) {
      throw invalid("The file is empty.");
    }
    if (file.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The file is larger than 10 MB.");
    }
    String name = cleanName(fileName);
    if (startsWith(file, 0xFF, 0xD8, 0xFF)) {
      return picture(name, file, "image/jpeg");
    }
    if (startsWith(file, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return picture(name, file, "image/png");
    }
    if (isPdf(file)) {
      return pdf(name, file);
    }
    if (startsWith(file, 'P', 'K', 0x03, 0x04)) {
      return docx(name, file);
    }
    if (startsWith(file, 'R', 'I', 'F', 'F')
        && file.length > 12
        && file[8] == 'W'
        && file[9] == 'E') {
      throw invalid(
          "WEBP pictures are converted by the page before they are attached. Try again, or attach a JPG or PNG.");
    }
    if (startsWith(file, 0xD0, 0xCF, 0x11, 0xE0)) {
      throw invalid(
          "Old .doc files cannot be read. Save the document as .docx or PDF and attach that.");
    }
    throw invalid("Attach a JPG, PNG, WEBP, PDF or DOCX file.");
  }

  // --- pictures --------------------------------------------------------------------------------

  private ReadFile picture(String name, byte[] file, String type) {
    if (!vision.available()) {
      throw new ApiException(
          ErrorCode.AI_UNAVAILABLE, "Reading pictures is not set up on this server yet.");
    }
    AiImage image = images.prepare(file, type);
    return new ReadFile(
        name, ReadFile.Kind.IMAGE, 0, 0, List.of(Segment.picture("", 0)), List.of(image), null);
  }

  // --- PDF -------------------------------------------------------------------------------------

  private ReadFile pdf(String name, byte[] file) {
    PdfReader.Content content = pdfs.read(file, vision.available());
    List<Segment> segments = new ArrayList<>();
    List<AiImage> pictures = new ArrayList<>();
    int letters = 0;
    for (PdfReader.Page page : content.read()) {
      String text = page.text().isEmpty() ? "(no text on this page)" : page.text();
      segments.add(Segment.text("[Page " + page.number() + "]\n" + text));
      for (int i = 0; i < page.text().length(); i++) {
        letters += Character.isLetterOrDigit(page.text().charAt(i)) ? 1 : 0;
      }
      if (page.picture() != null) {
        segments.add(Segment.picture("Page " + page.number() + " as a picture", pictures.size()));
        pictures.add(new AiImage("image/jpeg", page.picture()));
      }
    }
    if (letters < PdfReader.MIN_TEXT_CHARS && pictures.isEmpty()) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "No text could be read in this PDF. It looks scanned, and reading pictures is not set up on this server yet.");
    }
    List<String> notes = new ArrayList<>();
    int read = content.read().size();
    if (read < content.pages()) {
      notes.add(
          "Read pages 1 to " + read + " of " + content.pages() + ", because the document is long.");
    }
    if (content.unseen() > 0) {
      notes.add(
          content.unseen()
              + (content.unseen() == 1 ? " page has" : " pages have")
              + " a scan or a figure that was not read as a picture ("
              + (vision.available()
                  ? "the limit of " + PdfReader.MAX_PICTURES + " pictures"
                  : "picture reading is not set up here")
              + "), so only its text was read.");
    }
    return new ReadFile(
        name,
        ReadFile.Kind.PDF,
        content.pages(),
        read,
        segments,
        pictures,
        notes.isEmpty() ? null : String.join(" ", notes));
  }

  // --- DOCX ------------------------------------------------------------------------------------

  private ReadFile docx(String name, byte[] file) {
    DocxReader.Content content = documents.read(file);
    List<AiImage> pictures = new ArrayList<>();
    int[] newIndex = new int[content.pictures().size()];
    int dropped = 0;
    for (int i = 0; i < content.pictures().size(); i++) {
      newIndex[i] = -1;
      byte[] raw = content.pictures().get(i);
      String type = typeOf(raw);
      if (type == null || !vision.available()) {
        dropped++;
        continue;
      }
      try {
        newIndex[i] = pictures.size();
        pictures.add(images.prepare(raw, type));
      } catch (ApiException unreadable) {
        newIndex[i] = -1;
        dropped++;
      }
    }
    List<Segment> segments = new ArrayList<>();
    for (Segment segment : content.segments()) {
      if (!segment.hasPicture()) {
        segments.add(segment);
      } else if (newIndex[segment.picture()] >= 0) {
        segments.add(Segment.picture(segment.text(), newIndex[segment.picture()]));
      }
    }
    boolean anyText = segments.stream().anyMatch(s -> !s.hasPicture() && !s.text().isBlank());
    if (!anyText && pictures.isEmpty()) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED, "No readable text or pictures were found in this document.");
    }
    List<String> notes = new ArrayList<>();
    if (content.cut()) {
      notes.add("Only the first part of this long document was read.");
    }
    if (dropped > 0) {
      notes.add(
          dropped
              + (dropped == 1 ? " picture" : " pictures")
              + " in the document could not be read"
              + (vision.available() ? "." : " (picture reading is not set up here)."));
    }
    return new ReadFile(
        name,
        ReadFile.Kind.DOCX,
        0,
        0,
        segments,
        pictures,
        notes.isEmpty() ? null : String.join(" ", notes));
  }

  // --- small helpers ---------------------------------------------------------------------------

  private static String typeOf(byte[] file) {
    if (startsWith(file, 0xFF, 0xD8, 0xFF)) {
      return "image/jpeg";
    }
    if (startsWith(file, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return "image/png";
    }
    return null;
  }

  /** A PDF starts with "%PDF-", though a few files have a little text before it. */
  private static boolean isPdf(byte[] file) {
    int limit = Math.min(file.length - 5, 1024);
    for (int i = 0; i <= limit; i++) {
      if (file[i] == '%'
          && file[i + 1] == 'P'
          && file[i + 2] == 'D'
          && file[i + 3] == 'F'
          && file[i + 4] == '-') {
        return true;
      }
    }
    return false;
  }

  private static boolean startsWith(byte[] file, int... prefix) {
    if (file.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((file[i] & 0xFF) != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  /** A name safe to show and to put in a prompt: no path, no control characters, no brackets. */
  static String cleanName(String fileName) {
    String name = fileName == null ? "" : fileName;
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll("[\\p{Cntrl}\\[\\]<>\"]", " ").replaceAll("\\s+", " ").strip();
    if (name.length() > 80) {
      name = name.substring(0, 80).strip();
    }
    return name.isEmpty() ? "attachment" : name;
  }

  private static ApiException invalid(String message) {
    return new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, message);
  }
}
