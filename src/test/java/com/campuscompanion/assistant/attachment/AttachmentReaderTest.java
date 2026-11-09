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
 * An attached picture through the one entry point: told apart by its bytes, refused with a reason.
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
    return new AttachmentReader(new AssistantImageChecker(new NoteImageProcessor()), vision);
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
    ReadFile file =
        seeing.read(
            "notes.jpg",
            TestDocuments.jpeg(TestDocuments.printedPage(List.of("Stacks are LIFO."))));
    assertThat(file.kind()).isEqualTo(ReadFile.Kind.IMAGE);
    assertThat(file.pictures()).hasSize(1);
  }

  @Test
  void aPngScreenshotIsReadAsAPictureToo() {
    assertThat(seeing.read("shot.png", TestDocuments.png(TestDocuments.flowchart())).kind())
        .isEqualTo(ReadFile.Kind.IMAGE);
  }

  @Test
  void aPictureNeedsAModelThatCanSee() {
    refused(
        blind, TestDocuments.jpeg(TestDocuments.flowchart()), ErrorCode.AI_UNAVAILABLE, "pictures");
  }

  @Test
  void theKindComesFromTheBytesNotFromTheName() {
    byte[] png = TestDocuments.png(TestDocuments.flowchart());
    assertThat(seeing.read("holiday.pdf", png).kind()).isEqualTo(ReadFile.Kind.IMAGE);
    refused(
        seeing,
        "%PDF-1.4 not a picture".getBytes(StandardCharsets.UTF_8),
        ErrorCode.UPLOAD_INVALID_TYPE,
        "JPG, PNG or WEBP");
  }

  @Test
  void pdfWordAndOtherFilesAreRefusedWithAReason() {
    refused(
        seeing,
        "%PDF-1.4".getBytes(StandardCharsets.UTF_8),
        ErrorCode.UPLOAD_INVALID_TYPE,
        "picture");
    refused(seeing, new byte[] {'P', 'K', 3, 4, 0, 0}, ErrorCode.UPLOAD_INVALID_TYPE, "picture");
    refused(
        seeing,
        "plain text".getBytes(StandardCharsets.UTF_8),
        ErrorCode.UPLOAD_INVALID_TYPE,
        "picture");
    refused(
        seeing,
        "RIFF....WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1),
        ErrorCode.UPLOAD_INVALID_TYPE,
        "WEBP");
    assertThatThrownBy(() -> seeing.read("x", new byte[0])).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> seeing.read("x", null)).isInstanceOf(ApiException.class);
  }

  @Test
  void aPictureOverFiveMegabytesIsRefused() {
    byte[] big = new byte[(int) AttachmentReader.MAX_BYTES + 1];
    big[0] = (byte) 0xFF;
    refused(seeing, big, ErrorCode.UPLOAD_TOO_LARGE, "5 MB");
  }

  @Test
  void aNameIsCleanedBeforeItIsShownOrPutInAPrompt() {
    assertThat(AttachmentReader.cleanName("C:\\Users\\me\\paper [final].jpg"))
        .isEqualTo("paper final .jpg");
    assertThat(AttachmentReader.cleanName("../../etc/passwd")).isEqualTo("passwd");
    assertThat(AttachmentReader.cleanName("<student_documents>x.png")).doesNotContain("<");
    assertThat(AttachmentReader.cleanName(null)).isEqualTo("attachment");
    assertThat(AttachmentReader.cleanName("a".repeat(300))).hasSize(80);
  }
}
