package com.campuscompanion.notes;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.resource.PdfValidator;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Decides whether a file may be attached to a note. Every file is treated as hostile: it is at most
 * 10 MB, its declared type is on the list, and its first bytes agree with the declared type. A PDF
 * is also opened with PDFBox, and a text file must be text. The stored name is made by the server.
 *
 * <p>SVG pictures are not accepted: an SVG can carry script, and this application stores only the
 * types whose content it can check (docs/11-security-plan.md section 9).
 */
@Component
public class NoteFileChecker {

  public static final long MAX_BYTES = 10L * 1024 * 1024;

  /** What a file turned out to be. */
  public record Checked(String kind, String extension, String mimeType) {}

  private static final Map<String, String> IMAGES =
      Map.of("image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");

  private static final Map<String, String> FILES =
      Map.ofEntries(
          Map.entry("application/pdf", "pdf"),
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

  public NoteFileChecker(PdfValidator pdfs) {
    this.pdfs = pdfs;
  }

  public Checked check(byte[] content, String declaredType, String fileName) {
    if (content.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "Attachments must be under 10 MB.");
    }
    String type =
        declaredType == null ? "" : declaredType.toLowerCase(java.util.Locale.ROOT).strip();
    boolean image = IMAGES.containsKey(type);
    String extension = image ? IMAGES.get(type) : FILES.get(type);
    if (extension == null) {
      throw invalid("Upload an image, PDF, document, spreadsheet, or text file.");
    }
    if (content.length == 0 || !matchesType(content, type, fileName)) {
      throw invalid("That file does not match its type. Export it again and retry.");
    }
    return new Checked(image ? "image" : "file", extension, type);
  }

  private boolean matchesType(byte[] content, String type, String fileName) {
    return switch (type) {
      case "image/jpeg" -> startsWith(content, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
      case "image/png" ->
          startsWith(content, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
      case "image/gif" -> startsWith(content, new byte[] {'G', 'I', 'F', '8'});
      case "image/webp" ->
          startsWith(content, new byte[] {'R', 'I', 'F', 'F'})
              && content.length > 12
              && content[8] == 'W'
              && content[9] == 'E'
              && content[10] == 'B'
              && content[11] == 'P';
      case "application/pdf" -> pdfOpens(content, fileName);
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
    try {
      pdfs.validate(
          content,
          "application/pdf",
          fileName == null || !fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")
              ? "note.pdf"
              : fileName);
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
          .decode(java.nio.ByteBuffer.wrap(content));
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

  private static ApiException invalid(String message) {
    return new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, message);
  }
}
