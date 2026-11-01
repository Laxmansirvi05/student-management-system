package com.campuscompanion.assistant.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.provider.AiCompletion;
import com.campuscompanion.ai.provider.AiProvider;
import com.campuscompanion.ai.provider.AiRequest;
import com.campuscompanion.ai.provider.VisionProvider;
import com.campuscompanion.assistant.AssistantImageChecker;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.notes.NoteImageProcessor;
import com.campuscompanion.support.TestDocuments;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every kind of attachment through the one entry point: a file is told apart by its bytes, only
 * returned when something was really read, and refused with a reason when not.
 */
class AttachmentReaderTest {

  private static final AiProvider ANY =
      new AiProvider() {
        @Override
        public AiCompletion complete(AiRequest request) {
          return new AiCompletion("ok", 1, 1);
        }
      };

  private final AttachmentReader seeing = reader(new VisionProvider(ANY));
  private final AttachmentReader blind = reader(new VisionProvider(null));

  private static AttachmentReader reader(VisionProvider vision) {
    return new AttachmentReader(
        new AssistantImageChecker(new NoteImageProcessor()),
        new PdfReader(),
        new DocxReader(),
        vision);
  }

  private static void refused(AttachmentReader reader, byte[] file, ErrorCode code, String about) {
    assertThatThrownBy(() -> reader.read("x", file))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(code);
              assertThat(e.getMessage()).containsIgnoringCase(about);
            });
  }

  @Test
  void aPhotoOfPrintedNotesIsReadAsAPicture() {
    byte[] photo = TestDocuments.jpeg(TestDocuments.printedPage(List.of("Stacks are LIFO.")));
    ReadFile file = seeing.read("notes.jpg", photo);
    assertThat(file.kind()).isEqualTo(ReadFile.Kind.IMAGE);
    assertThat(file.pictures()).hasSize(1);
    assertThat(file.segments()).hasSize(1);
    assertThat(file.segments().get(0).hasPicture()).isTrue();
  }

  @Test
  void aPngScreenshotIsReadAsAPictureToo() {
    ReadFile file = seeing.read("shot.png", TestDocuments.png(TestDocuments.flowchart()));
    assertThat(file.kind()).isEqualTo(ReadFile.Kind.IMAGE);
  }

  @Test
  void aPictureNeedsAModelThatCanSee() {
    refused(
        blind, TestDocuments.jpeg(TestDocuments.flowchart()), ErrorCode.AI_UNAVAILABLE, "pictures");
  }

  @Test
  void aTextPdfIsReadWithoutNeedingAModelThatCanSee() {
    byte[] pdf =
        TestDocuments.textPdf(
            List.of(
                "Question 1. Define a stack and give two uses of it in a computer program.",
                "Question 2. Explain recursion with a short example in any language."));
    ReadFile file = blind.read("paper.pdf", pdf);
    assertThat(file.kind()).isEqualTo(ReadFile.Kind.PDF);
    assertThat(file.pages()).isEqualTo(2);
    assertThat(file.pagesRead()).isEqualTo(2);
    assertThat(file.pictures()).isEmpty();
    assertThat(file.segments().get(0).text()).startsWith("[Page 1]").contains("Question 1");
    assertThat(file.segments().get(1).text()).startsWith("[Page 2]").contains("Question 2");
    assertThat(file.notice()).isNull();
  }

  @Test
  void aScannedPdfIsGivenAsPagePictures() {
    byte[] pdf =
        TestDocuments.scannedPdf(
            List.of(
                TestDocuments.printedPage(List.of("Q1. Define a stack.")),
                TestDocuments.printedPage(List.of("Q2. Define a queue."))));
    ReadFile file = seeing.read("scan.pdf", pdf);
    assertThat(file.pictures()).hasSize(2);
    assertThat(file.segments()).anyMatch(Segment::hasPicture);
    assertThat(file.segments().stream().filter(Segment::hasPicture).map(Segment::text))
        .contains("Page 1 as a picture", "Page 2 as a picture");
  }

  @Test
  void aScannedPdfIsNotShownAsReadyWhenNothingCanBeRead() {
    byte[] pdf =
        TestDocuments.scannedPdf(
            List.of(TestDocuments.printedPage(List.of("Q1. Define a stack."))));
    refused(blind, pdf, ErrorCode.VALIDATION_FAILED, "scanned");
  }

  @Test
  void aWordDocumentIsReadAsText() {
    ReadFile file = blind.read("paper.docx", TestDocuments.questionPaperDocx(null));
    assertThat(file.kind()).isEqualTo(ReadFile.Kind.DOCX);
    assertThat(file.pictures()).isEmpty();
    assertThat(file.characters()).isGreaterThan(200);
  }

  @Test
  void aFigureInAWordDocumentGoesAsAPictureWhenThereIsAModelThatCanSee() {
    byte[] docx = TestDocuments.questionPaperDocx(TestDocuments.png(TestDocuments.flowchart()));
    ReadFile withSight = seeing.read("paper.docx", docx);
    assertThat(withSight.pictures()).hasSize(1);
    assertThat(withSight.notice()).isNull();
    ReadFile without = blind.read("paper.docx", docx);
    assertThat(without.pictures()).isEmpty();
    assertThat(without.notice()).contains("could not be read");
    assertThat(without.segments()).noneMatch(Segment::hasPicture);
  }

  @Test
  void theKindComesFromTheBytesNotFromTheNameOrTheClaimedType() {
    byte[] png = TestDocuments.png(TestDocuments.flowchart());
    assertThat(seeing.read("holiday.pdf", png).kind()).isEqualTo(ReadFile.Kind.IMAGE);
    byte[] pdf =
        TestDocuments.textPdf(
            List.of("A real PDF with enough readable words on the page to pass."));
    assertThat(seeing.read("notes.png", pdf).kind()).isEqualTo(ReadFile.Kind.PDF);
  }

  @Test
  void unreadableAndUnsupportedFilesAreRefusedWithAReason() {
    refused(
        seeing,
        "just some text".getBytes(StandardCharsets.UTF_8),
        ErrorCode.UPLOAD_INVALID_TYPE,
        "JPG, PNG, WEBP, PDF or DOCX");
    refused(
        seeing,
        new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0},
        ErrorCode.UPLOAD_INVALID_TYPE,
        ".docx");
    byte[] webp = "RIFF....WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
    refused(seeing, webp, ErrorCode.UPLOAD_INVALID_TYPE, "WEBP");
    assertThatThrownBy(() -> seeing.read("x", new byte[0])).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> seeing.read("x", null)).isInstanceOf(ApiException.class);
  }

  @Test
  void aFileOverTenMegabytesIsRefused() {
    byte[] big = new byte[(int) AttachmentReader.MAX_BYTES + 1];
    big[0] = (byte) 0xFF;
    refused(seeing, big, ErrorCode.UPLOAD_TOO_LARGE, "10 MB");
  }

  @Test
  void aNameIsCleanedBeforeItIsShownOrPutInAPrompt() {
    assertThat(AttachmentReader.cleanName("C:\\Users\\me\\paper [final].pdf"))
        .isEqualTo("paper final .pdf");
    assertThat(AttachmentReader.cleanName("../../etc/passwd")).isEqualTo("passwd");
    assertThat(AttachmentReader.cleanName("<student_documents>x.pdf")).doesNotContain("<");
    assertThat(AttachmentReader.cleanName(null)).isEqualTo("attachment");
    assertThat(AttachmentReader.cleanName("a".repeat(300))).hasSize(80);
  }
}
