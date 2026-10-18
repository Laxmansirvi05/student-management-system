package com.campuscompanion.assessment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * The CGPA rule (FR-MRK-002, docs/08-database-design.md 4.8), in plain Java so it can be tested on
 * its own.
 *
 * <p>CGPA = sum of (SGPA × credits registered) ÷ sum of (credits registered), over the published
 * semesters whose status is PASS or FAIL, rounded half up to two decimals. A WITHHELD semester is
 * left out until it is resolved. With no qualifying semester there is no CGPA (null).
 *
 * <p>While a subject is failed the college publishes no SGPA for that semester. Then there is no
 * CGPA either (null), because the college gives none until every subject is cleared.
 *
 * <p>The weight is the credits registered, not the credits earned: an SGPA already averages over
 * every registered course, with a failed course counting as zero, so weighting by registered
 * credits gives the true average across all courses.
 */
public final class CgpaCalculator {

  private CgpaCalculator() {}

  /** One semester as the rule needs it. */
  public record Term(BigDecimal sgpa, int creditsRegistered, ResultStatus status) {}

  public static BigDecimal cgpa(List<Term> publishedTerms) {
    BigDecimal weighted = BigDecimal.ZERO;
    long credits = 0;
    for (Term term : publishedTerms) {
      if (term.status() == ResultStatus.WITHHELD) {
        continue;
      }
      if (term.sgpa() == null) {
        return null;
      }
      weighted = weighted.add(term.sgpa().multiply(BigDecimal.valueOf(term.creditsRegistered())));
      credits += term.creditsRegistered();
    }
    if (credits == 0) {
      return null;
    }
    return weighted.divide(BigDecimal.valueOf(credits), 2, RoundingMode.HALF_UP);
  }
}
