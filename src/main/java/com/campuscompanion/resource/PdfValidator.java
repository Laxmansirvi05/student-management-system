package com.campuscompanion.resource;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

/**
 * Decides whether an uploaded file may be stored (docs/11-security-plan.md section 9). Every file
 * is treated as hostile, even though only administrators upload:
 *
 * <ol>
 *   <li>it is at most 10 MB;
 *   <li>its declared type and its extension say PDF;
 *   <li>its first bytes are the PDF signature;
 *   <li>PDFBox can open it within ten seconds, it is not encrypted, and it has a sensible number of
 *       pages.
 * </ol>
 */
@Component
public class PdfValidator {

  public static final long MAX_BYTES = 10L * 1024 * 1024;
  static final int MAX_PAGES = 2000;
  static final Duration PARSE_TIME_LIMIT = Duration.ofSeconds(10);

  /**
   * Opening a PDF runs on one of two threads of its own, so a file built to keep the parser busy
   * cannot hold a request for ever, and many such files cannot start many threads.
   */
  private static final ExecutorService PARSER =
      Executors.newFixedThreadPool(
          2,
          task -> {
            Thread thread = new Thread(task, "pdf-check");
            thread.setDaemon(true);
            return thread;
          });

  private final Duration parseTimeLimit;

  public PdfValidator() {
    this(PARSE_TIME_LIMIT);
  }

  /** For tests, which shorten the time limit. */
  PdfValidator(Duration parseTimeLimit) {
    this.parseTimeLimit = parseTimeLimit;
  }

  private static final byte[] SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

  /** Checks the file and returns its SHA-256 checksum as 64 hex characters. */
  public String validate(byte[] content, String declaredType, String fileName) {
    if (content.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The file is larger than 10 MB.");
    }
    if (content.length == 0
        || !"application/pdf".equalsIgnoreCase(declaredType)
        || fileName == null
        || !fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")
        || !startsWithSignature(content)
        || !opensInTime(content)) {
      throw new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, "Only PDF files can be uploaded.");
    }
    return sha256(content);
  }

  private static boolean startsWithSignature(byte[] content) {
    if (content.length < SIGNATURE.length) {
      return false;
    }
    for (int i = 0; i < SIGNATURE.length; i++) {
      if (content[i] != SIGNATURE[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * A file that cannot be opened within the time limit is refused like one that cannot be opened.
   */
  private boolean opensInTime(byte[] content) {
    Future<Boolean> opened = PARSER.submit(() -> opensAsPdf(content));
    try {
      return opened.get(parseTimeLimit.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException tooSlow) {
      opened.cancel(true);
      return false;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      return false;
    } catch (ExecutionException failed) {
      return false;
    }
  }

  /** A file that has the signature but cannot be opened is not a real PDF. */
  private static boolean opensAsPdf(byte[] content) {
    try (PDDocument document = Loader.loadPDF(content)) {
      int pages = document.getNumberOfPages();
      return !document.isEncrypted() && pages >= 1 && pages <= MAX_PAGES;
    } catch (IOException | RuntimeException notAPdf) {
      return false;
    }
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is part of every Java runtime", impossible);
    }
  }
}
