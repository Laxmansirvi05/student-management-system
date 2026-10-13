package com.campuscompanion.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import com.campuscompanion.assessment.CgpaCalculator.Term;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Task T2-10: the CGPA rule (FR-MRK-002), without Spring or a database. */
class CgpaCalculatorTest {

  @Test
  void weighsEachSgpaByTheCreditsRegisteredIncludingAFailedSemester() {
    // The worked example of docs/08-database-design.md 4.8: (7.80 x 22 + 6.50 x 24) / 46 = 7.12.
    List<Term> terms =
        List.of(term("7.80", 22, ResultStatus.PASS), term("6.50", 24, ResultStatus.FAIL));

    assertThat(CgpaCalculator.cgpa(terms)).isEqualTo(new BigDecimal("7.12"));
  }

  @Test
  void aWithheldSemesterIsLeftOut() {
    List<Term> terms =
        List.of(term("8.00", 20, ResultStatus.PASS), term("2.00", 20, ResultStatus.WITHHELD));

    assertThat(CgpaCalculator.cgpa(terms)).isEqualTo(new BigDecimal("8.00"));
  }

  @Test
  void thereIsNoCgpaWithoutAQualifyingSemester() {
    assertThat(CgpaCalculator.cgpa(List.of())).isNull();
    assertThat(CgpaCalculator.cgpa(List.of(term("9.00", 20, ResultStatus.WITHHELD)))).isNull();
  }

  @Test
  void oneSemesterGivesItsOwnSgpa() {
    assertThat(CgpaCalculator.cgpa(List.of(term("7.35", 22, ResultStatus.PASS))))
        .isEqualTo(new BigDecimal("7.35"));
  }

  @Test
  void theResultIsRoundedHalfUpToTwoDecimals() {
    // (7.00 x 1 + 8.00 x 2) / 3 = 7.6666..., which rounds up to 7.67.
    assertThat(
            CgpaCalculator.cgpa(
                List.of(term("7.00", 1, ResultStatus.PASS), term("8.00", 2, ResultStatus.PASS))))
        .isEqualTo(new BigDecimal("7.67"));
    // (7.01 + 7.02) / 2 = 7.015: the half rounds up to 7.02.
    assertThat(
            CgpaCalculator.cgpa(
                List.of(term("7.01", 1, ResultStatus.PASS), term("7.02", 1, ResultStatus.PASS))))
        .isEqualTo(new BigDecimal("7.02"));
  }

  private static Term term(String sgpa, int creditsRegistered, ResultStatus status) {
    return new Term(new BigDecimal(sgpa), creditsRegistered, status);
  }
}
