package com.campuscompanion.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;

/** Small files for upload tests, built in memory. Nothing here is a real document. */
public final class TestPdfs {

  private TestPdfs() {}

  /** A real PDF with the given number of blank pages. */
  public static byte[] valid(int pages) {
    try (PDDocument document = new PDDocument()) {
      for (int i = 0; i < pages; i++) {
        document.addPage(new PDPage());
      }
      return bytesOf(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A real PDF that needs a password to open. */
  public static byte[] passwordProtected() {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage());
      document.protect(
          new StandardProtectionPolicy("owner-secret", "user-secret", new AccessPermission()));
      return bytesOf(document);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Plain text: what a renamed .txt file contains. */
  public static byte[] plainText() {
    return "These are plain notes, not a PDF.".getBytes(StandardCharsets.UTF_8);
  }

  /** Starts like a PDF, but nothing after the first line is one. */
  public static byte[] signatureOnly() {
    return "%PDF-1.7\nthis only pretends to be a document\n".getBytes(StandardCharsets.UTF_8);
  }

  /** Starts like a PDF and is just over the 10 MB limit. */
  public static byte[] tooLarge() {
    byte[] content = new byte[10 * 1024 * 1024 + 1024];
    byte[] signature = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
    System.arraycopy(signature, 0, content, 0, signature.length);
    return content;
  }

  private static byte[] bytesOf(PDDocument document) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    document.save(out);
    return out.toByteArray();
  }
}
