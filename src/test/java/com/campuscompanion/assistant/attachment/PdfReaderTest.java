package com.campuscompanion.assistant.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.support.TestDocuments;
import com.campuscompanion.support.TestPdfs;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What is read from a PDF: text in page order, and pictures only of pages that need one. */
class PdfReaderTest {

  private final PdfReader reader = new PdfReader();

  @Test
  void aTextPdfGivesItsPagesInOrderWithoutAnyPicture() {
    byte[] file =
        TestDocuments.textPdf(
            List.of(
                "Question 1. Define a queue.\nQuestion 2. What is a stack? Give two uses.",
                "Question 3. Explain binary search with an example.\nQuestion 4. State the time complexity.",
                "Question 5. Write a program to reverse a linked list."));
    PdfReader.Content content = reader.read(file, true);
    assertThat(content.pages()).isEqualTo(3);
    assertThat(content.read()).hasSize(3);
    assertThat(content.read().get(0).text())
        .contains("Question 1. Define a queue.")
        .contains("Question 2");
    assertThat(content.read().get(1).text()).contains("Question 3").contains("Question 4");
    assertThat(content.read().get(2).text()).contains("Question 5");
    assertThat(content.read()).allSatisfy(page -> assertThat(page.picture()).isNull());
    assertThat(content.unseen()).isZero();
  }

  @Test
  void aScannedPdfHasNoTextSoEveryPageIsGivenAsAPicture() {
    byte[] file =
        TestDocuments.scannedPdf(
            List.of(
                TestDocuments.printedPage(List.of("Q1. What is a stack?")),
                TestDocuments.printedPage(List.of("Q2. What is a queue?"))));
    PdfReader.Content content = reader.read(file, true);
    assertThat(content.read()).hasSize(2);
    for (PdfReader.Page page : content.read()) {
      assertThat(page.text()).isEmpty();
      assertThat(page.picture()).isNotNull();
      assertThat(page.picture()[0]).isEqualTo((byte) 0xFF); // a JPEG
    }
  }

  @Test
  void aDiagramOnATextPageIsGivenAsAPictureToo() {
    byte[] file =
        TestDocuments.pdfWithDiagram(
            "Figure 1 shows the working of a queue. Study the figure and answer the question that follows it.",
            TestDocuments.flowchart());
    PdfReader.Content content = reader.read(file, true);
    assertThat(content.read().get(0).text()).contains("Figure 1 shows the working of a queue");
    assertThat(content.read().get(0).picture()).isNotNull();
  }

  @Test
  void withoutPictureReadingScannedPagesAreCountedNotDrawn() {
    byte[] file = TestDocuments.scannedPdf(List.of(TestDocuments.printedPage(List.of("Q1"))));
    PdfReader.Content content = reader.read(file, false);
    assertThat(content.read().get(0).picture()).isNull();
    assertThat(content.unseen()).isEqualTo(1);
  }

  @Test
  void onlyTheFirstEightPagesAreDrawn() {
    List<java.awt.image.BufferedImage> scans = new ArrayList<>();
    for (int i = 0; i < PdfReader.MAX_PICTURES + 2; i++) {
      scans.add(TestDocuments.printedPage(List.of("Page " + i)));
    }
    PdfReader.Content content = reader.read(TestDocuments.scannedPdf(scans), true);
    long drawn = content.read().stream().filter(page -> page.picture() != null).count();
    assertThat(drawn).isEqualTo(PdfReader.MAX_PICTURES);
    assertThat(content.unseen()).isEqualTo(2);
  }

  @Test
  void aPasswordProtectedPdfIsRefusedWithAClearMessage() {
    assertThatThrownBy(() -> reader.read(TestPdfs.passwordProtected(), true))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(ErrorCode.UPLOAD_INVALID_TYPE);
              assertThat(e.getMessage()).contains("password");
            });
  }

  @Test
  void bytesThatAreNotAPdfAreRefused() {
    assertThatThrownBy(
            () -> reader.read("%PDF-1.4 but not really".getBytes(StandardCharsets.UTF_8), true))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UPLOAD_INVALID_TYPE));
  }

  @Test
  void aPageOfSymbolsIsTreatedAsNeedingAPicture() {
    assertThat(
            PdfReader.needsPicture(
                new org.apache.pdfbox.pdmodel.PDPage(), "ÿþ¤¥§¶ÿþ¤¥§¶ÿþ¤¥§¶ÿþ¤¥§¶ÿþ¤¥§¶ÿþ¤¥§¶"))
        .isTrue();
    assertThat(
            PdfReader.needsPicture(
                new org.apache.pdfbox.pdmodel.PDPage(),
                "This page has plenty of ordinary readable text in it, so its text can be trusted."))
        .isFalse();
  }

  @Test
  void tidyKeepsLinesAndDropsRunsOfBlankLines() {
    assertThat(PdfReader.tidy("a  \r\n\r\n\r\n\r\nb \n")).isEqualTo("a\n\nb");
  }
}
