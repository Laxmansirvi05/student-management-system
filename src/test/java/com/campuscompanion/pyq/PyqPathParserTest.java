package com.campuscompanion.pyq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.pyq.dto.PyqMetadata;
import org.junit.jupiter.api.Test;

class PyqPathParserTest {

  @Test
  void readsBranchSubjectAndTheExam() {
    PyqMetadata paper = PyqPathParser.parse("CSE/Machine Learning/2025(December)-supply.pdf");

    assertThat(paper.branch()).isEqualTo("CSE");
    assertThat(paper.subject()).isEqualTo("Machine Learning");
    assertThat(paper.year()).isEqualTo(2025);
    assertThat(paper.month()).isEqualTo(12);
    assertThat(paper.kind()).isEqualTo(PyqExamKind.SUPPLY);
    assertThat(paper.midNumber()).isNull();
    assertThat(paper.regulation()).isNull();
    assertThat(paper.variant()).isEqualTo(1);
  }

  @Test
  void readsARegulationAndAQuestionSetNumber() {
    PyqMetadata paper = PyqPathParser.parse("ECE/Digital Design/2026(May)-regular AR22 (2).pdf");

    assertThat(paper.regulation()).isEqualTo("AR22");
    assertThat(paper.variant()).isEqualTo(2);
    assertThat(paper.kind()).isEqualTo(PyqExamKind.REGULAR);
  }

  @Test
  void aCourseCodeInPlaceOfTheRegulationIsKept() {
    PyqMetadata paper =
        PyqPathParser.parse("CSE/Cryptography/2025(December)-regular 20CS31009.pdf");

    assertThat(paper.courseCode()).isEqualTo("20CS31009");
    assertThat(paper.regulation()).isNull();
  }

  @Test
  void aMidPaperMayHaveANumber() {
    assertThat(PyqPathParser.parse("EEE/Electrical Machines - I/2023(August)-mid.pdf").midNumber())
        .isNull();
    PyqMetadata second = PyqPathParser.parse("EEE/Electrical Machines - I/2023(August)-mid-2.pdf");
    assertThat(second.kind()).isEqualTo(PyqExamKind.MID);
    assertThat(second.midNumber()).isEqualTo(2);
  }

  @Test
  void aLongerPathUsesItsLastThreeParts() {
    PyqMetadata paper =
        PyqPathParser.parse("papers-sorted/Mech/Thermodynamics/2024(January)-regular.pdf");

    assertThat(paper.branch()).isEqualTo("MECH");
    assertThat(paper.subject()).isEqualTo("Thermodynamics");
  }

  @Test
  void windowsSeparatorsAndAnyLetterCaseWork() {
    PyqMetadata paper = PyqPathParser.parse("fe\\Engineering Graphics\\2023(august)-SUPPLY.PDF");

    assertThat(paper.branch()).isEqualTo("FE");
    assertThat(paper.month()).isEqualTo(8);
    assertThat(paper.kind()).isEqualTo(PyqExamKind.SUPPLY);
  }

  @Test
  void refusesPathsItCannotReadAndSaysWhy() {
    assertThat(messageOf(null)).contains("Give the path");
    assertThat(messageOf("2025(May)-regular.pdf")).contains("branch, subject and file name");
    assertThat(messageOf("CSE/ML/paper.pdf")).contains("2025(December)-supply.pdf");
    assertThat(messageOf("CSE/ML/2025(Decembre)-supply.pdf")).contains("not a month");
    assertThat(messageOf("CSE/ML/2025(May)-regular-2.pdf"))
        .contains("Only a mid paper has a number");
    assertThat(messageOf("CSE/ML/2025(May)-final.pdf")).contains("must look like");
  }

  /** The reason the parser gives for refusing a path, on the field "path". */
  private static String messageOf(String path) {
    ValidationException refused =
        catchThrowableOfType(ValidationException.class, () -> PyqPathParser.parse(path));
    assertThat(refused).isNotNull();
    assertThat(refused.getErrors()).extracting(FieldErrorDetail::field).containsExactly("path");
    return refused.getErrors().get(0).message();
  }
}
