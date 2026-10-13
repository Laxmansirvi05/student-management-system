package com.campuscompanion.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.support.TestPdfs;
import org.junit.jupiter.api.Test;

/** The upload checks of docs/11-security-plan.md section 9, one rule at a time. */
class PdfValidatorTest {

  private static final String PDF = "application/pdf";

  private final PdfValidator validator = new PdfValidator();

  @Test
  void acceptsARealPdfAndReturnsItsChecksum() {
    byte[] pdf = TestPdfs.valid(2);

    String checksum = validator.validate(pdf, PDF, "unit-1.pdf");

    assertThat(checksum).hasSize(64).matches("[0-9a-f]+");
    assertThat(validator.validate(pdf, PDF, "another-name.PDF")).isEqualTo(checksum);
  }

  @Test
  void rejectsTextRenamedToPdf() {
    assertInvalidType(TestPdfs.plainText(), PDF, "notes.pdf");
  }

  @Test
  void rejectsAFileThatHasTheSignatureButCannotBeOpened() {
    assertInvalidType(TestPdfs.signatureOnly(), PDF, "broken.pdf");
  }

  @Test
  void rejectsARealPdfDeclaredAsAnotherType() {
    assertInvalidType(TestPdfs.valid(1), "text/plain", "notes.pdf");
    assertInvalidType(TestPdfs.valid(1), null, "notes.pdf");
  }

  @Test
  void rejectsARealPdfWithAnotherExtension() {
    assertInvalidType(TestPdfs.valid(1), PDF, "notes.pdf.exe");
    assertInvalidType(TestPdfs.valid(1), PDF, null);
  }

  @Test
  void rejectsAnEmptyFileAndAPdfWithoutPages() {
    assertInvalidType(new byte[0], PDF, "empty.pdf");
    assertInvalidType(TestPdfs.valid(0), PDF, "no-pages.pdf");
  }

  @Test
  void rejectsAPasswordProtectedPdf() {
    assertInvalidType(TestPdfs.passwordProtected(), PDF, "locked.pdf");
  }

  @Test
  void rejectsAFileOverTenMegabytesBeforeLookingInside() {
    assertThatThrownBy(() -> validator.validate(TestPdfs.tooLarge(), PDF, "huge.pdf"))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.getCode()).isEqualTo(ErrorCode.UPLOAD_TOO_LARGE));
  }

  private void assertInvalidType(byte[] content, String declaredType, String fileName) {
    assertThatThrownBy(() -> validator.validate(content, declaredType, fileName))
        .isInstanceOfSatisfying(
            ApiException.class,
            failure -> assertThat(failure.getCode()).isEqualTo(ErrorCode.UPLOAD_INVALID_TYPE));
  }
}
