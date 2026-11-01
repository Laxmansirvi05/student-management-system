package com.campuscompanion.assistant.attachment;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.ai.provider.AiImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What the model is given for a question with files: the text, the markers and the pictures. */
class DocumentContextTest {

  private static AiImage image(int n) {
    return new AiImage("image/jpeg", new byte[] {(byte) n});
  }

  private static ReadFile pdf(
      String name, int pages, List<Segment> segments, List<AiImage> pictures) {
    return new ReadFile(name, ReadFile.Kind.PDF, pages, pages, segments, pictures, null);
  }

  @Test
  void textAndPicturesComeInTheSameOrderAsTheirMarkers() {
    ReadFile scanned =
        pdf(
            "scan.pdf",
            2,
            List.of(
                Segment.text("[Page 1]\n(no text on this page)"),
                Segment.picture("Page 1 as a picture", 0),
                Segment.text("[Page 2]\nQ2. Define a queue."),
                Segment.picture("Page 2 as a picture", 1)),
            List.of(image(1), image(2)));
    ReadFile photo =
        new ReadFile(
            "notes.jpg",
            ReadFile.Kind.IMAGE,
            0,
            0,
            List.of(Segment.picture("", 0)),
            List.of(image(3)),
            null);
    DocumentContext.Built built = DocumentContext.build(List.of(scanned, photo));
    assertThat(built.pictures())
        .extracting(p -> p.data()[0])
        .containsExactly((byte) 1, (byte) 2, (byte) 3);
    assertThat(built.text())
        .contains("Document 1: \"scan.pdf\" (PDF, 2 pages)")
        .contains("[Picture 1: Page 1 as a picture]")
        .contains("[Picture 2: Page 2 as a picture]")
        .contains("Document 2: \"notes.jpg\" (a picture)")
        .contains("[Picture 3: a picture in this document]");
    assertThat(built.text().indexOf("Picture 2")).isLessThan(built.text().indexOf("Document 2"));
  }

  @Test
  void noFilesGiveNothing() {
    assertThat(DocumentContext.build(List.of()).isEmpty()).isTrue();
  }

  @Test
  void aLongDocumentIsCutAndTheModelIsToldSo() {
    List<Segment> segments = new ArrayList<>();
    for (int page = 1; page <= 40; page++) {
      segments.add(Segment.text("[Page " + page + "]\n" + "text ".repeat(300)));
    }
    DocumentContext.Built built =
        DocumentContext.build(List.of(pdf("long.pdf", 40, segments, List.of())));
    assertThat(built.text().length()).isLessThan(DocumentContext.MAX_CHARS + 600);
    assertThat(built.text()).contains("[Page 1]").contains("not included here because it is long");
    assertThat(built.text()).doesNotContain("[Page 40]");
    assertThat(built.shortened()).isTrue();
  }

  @Test
  void theTextBudgetIsSharedBetweenFiles() {
    List<Segment> segments = new ArrayList<>();
    for (int page = 1; page <= 30; page++) {
      segments.add(Segment.text("[Page " + page + "]\n" + "word ".repeat(400)));
    }
    ReadFile a = pdf("a.pdf", 30, segments, List.of());
    ReadFile b = pdf("b.pdf", 30, segments, List.of());
    DocumentContext.Built built = DocumentContext.build(List.of(a, b));
    assertThat(built.text()).contains("Document 1").contains("Document 2");
    assertThat(built.text().length()).isLessThan(DocumentContext.MAX_CHARS + 1200);
  }

  @Test
  void morePicturesThanTheLimitAreNotSentAndTheTextSaysWhich() {
    List<Segment> segments = new ArrayList<>();
    List<AiImage> pictures = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      segments.add(Segment.picture("Page " + (i + 1) + " as a picture", i));
      pictures.add(image(i));
    }
    DocumentContext.Built built =
        DocumentContext.build(List.of(pdf("scan.pdf", 8, segments, pictures)));
    assertThat(built.pictures()).hasSize(DocumentContext.MAX_PICTURES);
    assertThat(built.text())
        .contains("[Picture 5:")
        .doesNotContain("[Picture 6:")
        .contains("is not shown");
    assertThat(built.shortened()).isTrue();
  }

  @Test
  void aNoticeAboutTheFileTravelsWithIt() {
    ReadFile file =
        new ReadFile(
            "p.pdf",
            ReadFile.Kind.PDF,
            90,
            60,
            List.of(Segment.text("[Page 1]\nhi")),
            List.of(),
            "Read pages 1 to 60 of 90.");
    assertThat(DocumentContext.build(List.of(file)).text())
        .contains("[Note: Read pages 1 to 60 of 90.]");
  }
}
