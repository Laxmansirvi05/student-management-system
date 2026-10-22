package com.campuscompanion.notes;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.resource.PdfValidator;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Decides whether a file may be attached to a note, and makes what is stored. Every file is treated
 * as hostile: it is at most 10 MB, its declared type is on the list, and its first bytes agree with
 * the declared type.
 *
 * <p>A picture (JPEG, PNG or GIF) is not stored as it came: {@link NoteImageProcessor} draws a new
 * JPEG from it. A PDF is opened with PDFBox. Other documents (see {@link NotesProperties}) are
 * accepted only when their type has been switched on, and are checked by their first bytes; a text
 * file must be text. SVG and WebP pictures are not accepted: an SVG can carry script, and the JDK
 * cannot read WebP, so neither can be checked (docs/11-security-plan.md section 9).
 */
@Component
public class NoteFileChecker {

  public static final long MAX_BYTES = 10L * 1024 * 1024;

  /** What is stored for an upload: its kind, the extension and content type, and the bytes. */
  public record Prepared(String kind, String extension, String mimeType, byte[] content) {}

  private static final Map<String, String> PICTURES =
      Map.of("image/jpeg", "jpeg", "image/png", "png", "image/gif", "gif");

  private static final Map<String, String> DOCUMENTS =
      Map.ofEntries(
          Map.entry("application/msword", "doc"),
          Map.entry(
              "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx"),
          Map.entry("application/vnd.ms-excel", "xls"),
          Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
          Map.entry("application/vnd.ms-powerpoint", "ppt"),
          Map.entry(
              "application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx"),
          Map.entry("text/plain", "txt"),
          Map.entry("text/csv", "csv"),
          Map.entry("text/markdown", "md"),
          Map.entry("application/zip", "zip"));

  private static final byte[] OLE = {
    (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
  };
  private static final byte[] ZIP = {'P', 'K', 3, 4};

  private final PdfValidator pdfs;
  private final NoteImageProcessor pictures;
  private final Set<String> extraTypes;

  public NoteFileChecker(
      PdfValidator pdfs, NoteImageProcessor pictures, NotesProperties properties) {
    this.pdfs = pdfs;
    this.pictures = pictures;
    this.extraTypes =
        properties.extraFileTypes().stream()
            .map(type -> type.strip().toLowerCase(Locale.ROOT))
            .filter(type -> !type.isEmpty())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  public Prepared prepare(byte[] content, String declaredType, String fileName) {
    if (content.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "Attachments must be under 10 MB.");
    }
    String type = declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT).strip();
    String picture = PICTURES.get(type);
    if (picture != null) {
      if (content.length == 0 || !matchesPicture(content, picture)) {
        throw mismatch();
      }
      return new Prepared("image", "jpg", "image/jpeg", pictures.toJpeg(content, picture));
    }
    if ("application/pdf".equals(type)) {
      if (content.length == 0 || !pdfOpens(content, fileName)) {
        throw mismatch();
      }
      return new Prepared("file", "pdf", type, content);
    }
    String extension = DOCUMENTS.get(type);
    if (extension == null || !extraTypes.contains(extension)) {
      throw invalid("Attach a PDF, or a JPEG, PNG or GIF picture.");
    }
    if (content.length == 0 || !matchesDocument(content, type)) {
      throw mismatch();
    }
    return new Prepared("file", extension, type, content);
  }

  private static boolean matchesPicture(byte[] content, String format) {
    return switch (format) {
      case "jpeg" -> startsWith(content, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
      case "png" ->
          startsWith(content, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
      default -> startsWith(content, new byte[] {'G', 'I', 'F', '8'});
    };
  }

  private static boolean matchesDocument(byte[] content, String type) {
    return switch (type) {
      case "application/msword", "application/vnd.ms-excel", "application/vnd.ms-powerpoint" ->
          startsWith(content, OLE);
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          "application/vnd.openxmlformats-officedocument.presentationml.presentation",
          "application/zip" ->
          startsWith(content, ZIP);
      default -> isText(content);
    };
  }

  private boolean pdfOpens(byte[] content, String fileName) {
    boolean named = fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".pdf");
    try {
      pdfs.validate(content, "application/pdf", named ? fileName : "note.pdf");
      return true;
    } catch (ApiException notAPdf) {
      return false;
    }
  }

  /** Valid UTF-8 with no NUL byte: what a text, CSV or Markdown file looks like. */
  private static boolean isText(byte[] content) {
    for (byte b : content) {
      if (b == 0) {
        return false;
      }
    }
    try {
      StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(content));
      return true;
    } catch (CharacterCodingException notText) {
      return false;
    }
  }

  private static boolean startsWith(byte[] content, byte[] prefix) {
    if (content.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (content[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static ApiException mismatch() {
    return invalid("That file does not match its type. Export it again and retry.");
  }

  private static ApiException invalid(String message) {
    return new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, message);
  }
}
