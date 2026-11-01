package com.campuscompanion.assistant.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.support.TestDocuments;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** What is read from a Word document: its structure, its numbers, its tables and its pictures. */
class DocxReaderTest {

  private final DocxReader reader = new DocxReader();

  private static String text(DocxReader.Content content) {
    StringBuilder all = new StringBuilder();
    for (Segment segment : content.segments()) {
      all.append(segment.hasPicture() ? "<<" + segment.text() + ">>" : segment.text()).append('\n');
    }
    return all.toString();
  }

  @Test
  void headingsListsAndQuestionNumbersSurvive() {
    DocxReader.Content content = reader.read(TestDocuments.questionPaperDocx(null));
    String text = text(content);
    assertThat(text)
        .contains("# Data Structures Internal Test 2")
        .contains("## Section A")
        .contains("Time: 1 hour. Answer all questions.")
        .contains("1. Define a stack and state two applications.")
        .contains("2. Convert the infix expression A+B*C to postfix.")
        .contains("  (a) Show each step of the conversion.")
        .contains("3. Study the table of marks and answer.");
    assertThat(text.indexOf("1. Define")).isLessThan(text.indexOf("2. Convert"));
  }

  @Test
  void aTableBecomesAMarkdownTable() {
    String text = text(reader.read(TestDocuments.questionPaperDocx(null)));
    assertThat(text)
        .contains("| Question | Marks |")
        .contains("| --- | --- |")
        .contains("| 1 | 5 |")
        .contains("| 3 | 10 |");
  }

  @Test
  void anEquationIsWrittenAsReadableText() {
    String text = text(reader.read(TestDocuments.questionPaperDocx(null)));
    assertThat(text).contains("$(a)/(b)+c^(2)$");
  }

  @Test
  void bulletsAreMarkedAndNotNumbered() {
    byte[] file =
        TestDocuments.docx(
            TestDocuments.bullet("first point") + TestDocuments.bullet("second"), null);
    assertThat(text(reader.read(file))).contains("- first point").contains("- second");
  }

  @Test
  void aPictureIsReturnedAtItsPlaceWithItsDescription() {
    byte[] diagram = TestDocuments.png(TestDocuments.flowchart());
    DocxReader.Content content = reader.read(TestDocuments.questionPaperDocx(diagram));
    assertThat(content.pictures()).hasSize(1);
    assertThat(content.pictures().get(0)).isEqualTo(diagram);
    assertThat(text(content))
        .contains("## Section B")
        .contains("<<Picture description: Flowchart of a queue");
    Segment last = content.segments().get(content.segments().size() - 1);
    assertThat(last.hasPicture()).isTrue();
  }

  @Test
  void aFileThatIsNotAWordDocumentIsRefused() throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry("readme.txt"));
      zip.write("hello".getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    }
    assertThatThrownBy(() -> reader.read(bytes.toByteArray()))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UPLOAD_INVALID_TYPE));
    assertThatThrownBy(() -> reader.read("not a zip".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void aDocumentWithADoctypeIsRefusedBeforeAnyEntityIsRead() {
    String evil =
        "<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body><w:p><w:r><w:t>&x;</w:t></w:r></w:p></w:body></w:document>";
    byte[] file = replaceBody(evil);
    assertThatThrownBy(() -> reader.read(file))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UPLOAD_INVALID_TYPE));
  }

  @Test
  void aSmallZipThatUnpacksToAHugeFileIsRefused() {
    String filler = " ".repeat(DocxReader.MAX_XML_BYTES + 1024);
    byte[] file =
        replaceBody(
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                + "<w:body><w:p><w:r><w:t>"
                + filler
                + "</w:t></w:r></w:p></w:body></w:document>");
    assertThat(file.length).isLessThan(100_000);
    assertThatThrownBy(() -> reader.read(file))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.getCode()).isEqualTo(ErrorCode.UPLOAD_TOO_LARGE));
  }

  @Test
  void aVeryLongDocumentIsReadOnlyAsFarAsTheLimit() {
    StringBuilder body = new StringBuilder();
    for (int i = 0; i < 2000; i++) {
      body.append(TestDocuments.paragraph("Paragraph " + i + " " + "word ".repeat(12)));
    }
    DocxReader.Content content = reader.read(TestDocuments.docx(body.toString(), null));
    assertThat(content.cut()).isTrue();
    assertThat(text(content).length()).isLessThanOrEqualTo(DocxReader.MAX_CHARS + 10);
    assertThat(text(content)).contains("Paragraph 0 ").doesNotContain("Paragraph 1999 ");
  }

  /** A copy of a valid document whose body part is replaced by the given XML. */
  private static byte[] replaceBody(String xml) {
    try {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
        zip.putNextEntry(new ZipEntry("word/document.xml"));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
      return bytes.toByteArray();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  @Test
  void anEmptyDocumentGivesNoSegments() {
    DocxReader.Content content = reader.read(TestDocuments.docx("", null));
    assertThat(content.segments()).isEmpty();
    assertThat(List.copyOf(content.pictures())).isEmpty();
  }
}
