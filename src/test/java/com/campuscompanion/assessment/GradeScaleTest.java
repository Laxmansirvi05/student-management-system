package com.campuscompanion.assessment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GradeScaleTest {

  @Test
  void followsTheCollegeTable() {
    assertThat(GradeScale.points("O")).isEqualTo(10);
    assertThat(GradeScale.points("A+")).isEqualTo(9);
    assertThat(GradeScale.points("A")).isEqualTo(8);
    assertThat(GradeScale.points("B+")).isEqualTo(7);
    assertThat(GradeScale.points("B")).isEqualTo(6);
    assertThat(GradeScale.points("C")).isEqualTo(5);
    assertThat(GradeScale.points("F")).isZero();
    assertThat(GradeScale.points("Ab")).isZero();
    assertThat(GradeScale.points("S")).isNull();
  }

  @Test
  void cIsTheLowestPass() {
    assertThat(GradeScale.passed("C")).isTrue();
    assertThat(GradeScale.passed("S")).isTrue();
    assertThat(GradeScale.passed("F")).isFalse();
    assertThat(GradeScale.passed("Ab")).isFalse();
    assertThat(GradeScale.passed("U")).isFalse();
  }
}
