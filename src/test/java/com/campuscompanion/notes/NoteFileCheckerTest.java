package com.campuscompanion.notes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.resource.PdfValidator;
import com.campuscompanion.support.TestImages;
import com.campuscompanion.support.TestPdfs;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What may be attached to a note, and what is stored for it. */
class NoteFileCheckerTest {

  private static final String DOCX =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private static NoteFileChecker checker(String... extra) {
    return new NoteFileChecker(
        new PdfValidator(), new NoteImageProcessor(), new NotesProperties(List.of(extra)));
  }

  private static void assertRefused(Runnable action, ErrorCode code) {
    assertThatThrownBy(action::run)
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).getCode())
        .isEqualTo(code);
  }

  @Test
  void aPictureIsStoredAsANewJpegOnWhite() {
    NoteFileChecker.Prepared prepared =
        checker().prepare(TestImages.pngWithTransparency(40, 30), "image/png", "a.png");
    assertThat(prepared.kind()).isEqualTo("image");
    assertThat(prepared.extension()).isEqualTo("jpg");
    assertThat(prepared.mimeType()).isEqualTo("image/jpeg");
    assertThat(prepared.content()[0]).isEqualTo((byte) 0xFF);
    assertThat(prepared.content()[1]).isEqualTo((byte) 0xD8);
    java.awt.image.BufferedImage stored = TestImages.read(prepared.content());
    assertThat(stored.getWidth()).isEqualTo(40);
    assertThat(stored.getHeight()).isEqualTo(30);
    // A transparent pixel becomes white, never black.
    assertThat(stored.getRGB(0, 0) & 0xFFFFFF).isGreaterThan(0xF0F0F0);
  }

  @Test
  void aGifKeepsItsFirstFrameAsAJpeg() {
    NoteFileChecker.Prepared prepared =
        checker().prepare(TestImages.gif(30, 30), "image/gif", "a.gif");
    assertThat(prepared.mimeType()).isEqualTo("image/jpeg");
  }

  @Test
  void aPictureThatIsTooLargeToDecodeSafelyIsRefusedBeforeItIsDecoded() {
    byte[] wide = TestImages.pngWithTransparency(NoteImageProcessor.MAX_SIDE + 1, 2);
    assertRefused(
        () -> checker().prepare(wide, "image/png", "wide.png"), ErrorCode.UPLOAD_INVALID_TYPE);
  }

  @Test
  void aPictureMustMatchItsDeclaredType() {
    assertRefused(
        () -> checker().prepare(TestImages.jpeg(30, 30), "image/png", "a.png"),
        ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(
        () -> checker().prepare("nothing".getBytes(), "image/jpeg", "a.jpg"),
        ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(
        () -> checker().prepare(new byte[0], "image/png", "a.png"), ErrorCode.UPLOAD_INVALID_TYPE);
  }

  @Test
  void svgAndWebpPicturesAreNotAccepted() {
    byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(StandardCharsets.UTF_8);
    assertRefused(
        () -> checker().prepare(svg, "image/svg+xml", "a.svg"), ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(
        () ->
            checker()
                .prepare(
                    new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'},
                    "image/webp",
                    "a.webp"),
        ErrorCode.UPLOAD_INVALID_TYPE);
  }

  @Test
  void aPdfIsOpenedAndKeptAsItIs() {
    byte[] pdf = TestPdfs.valid(1);
    NoteFileChecker.Prepared prepared = checker().prepare(pdf, "application/pdf", "notes.pdf");
    assertThat(prepared.kind()).isEqualTo("file");
    assertThat(prepared.content()).isEqualTo(pdf);
    assertRefused(
        () -> checker().prepare(TestPdfs.signatureOnly(), "application/pdf", "x.pdf"),
        ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(
        () -> checker().prepare(TestPdfs.passwordProtected(), "application/pdf", "x.pdf"),
        ErrorCode.UPLOAD_INVALID_TYPE);
  }

  @Test
  void otherDocumentsNeedTheirTypeSwitchedOn() {
    byte[] zip = {'P', 'K', 3, 4, 0, 0, 0, 0};
    assertRefused(() -> checker().prepare(zip, DOCX, "a.docx"), ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(() -> checker("txt").prepare(zip, DOCX, "a.docx"), ErrorCode.UPLOAD_INVALID_TYPE);

    NoteFileChecker.Prepared docx = checker("docx", " TXT ").prepare(zip, DOCX, "a.docx");
    assertThat(docx.extension()).isEqualTo("docx");
    assertThat(docx.mimeType()).isEqualTo(DOCX);
    assertThat(
            checker("docx", " TXT ")
                .prepare("plain".getBytes(StandardCharsets.UTF_8), "text/plain", "a.txt")
                .extension())
        .isEqualTo("txt");
  }

  @Test
  void aDocumentMustMatchItsType() {
    assertRefused(
        () -> checker("docx").prepare("plain".getBytes(), DOCX, "a.docx"),
        ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(
        () -> checker("txt").prepare(new byte[] {1, 2, 0, 3}, "text/plain", "a.txt"),
        ErrorCode.UPLOAD_INVALID_TYPE);
    assertRefused(
        () -> checker("txt").prepare(new byte[] {(byte) 0xC3, 0x28}, "text/plain", "a.txt"),
        ErrorCode.UPLOAD_INVALID_TYPE);
  }

  @Test
  void aFileOverTenMegabytesIsRefused() {
    assertRefused(
        () ->
            checker()
                .prepare(
                    new byte[(int) NoteFileChecker.MAX_BYTES + 1], "application/pdf", "big.pdf"),
        ErrorCode.UPLOAD_TOO_LARGE);
  }

  @Test
  void aPictureIsTurnedUpright() {
    java.awt.image.BufferedImage wide =
        new java.awt.image.BufferedImage(40, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
    assertThat(NoteImageProcessor.upright(wide, 6).getWidth()).isEqualTo(20);
    assertThat(NoteImageProcessor.upright(wide, 8).getHeight()).isEqualTo(40);
    assertThat(NoteImageProcessor.upright(wide, 3).getWidth()).isEqualTo(40);
    assertThat(NoteImageProcessor.upright(wide, 1)).isSameAs(wide);
  }
}
