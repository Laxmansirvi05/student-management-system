package com.campuscompanion.assessment.dto;

import java.math.BigDecimal;
import java.util.List;

/** A student's published semester results and the CGPA calculated from them. */
public record ResultsResponse(BigDecimal cgpa, List<SemesterResultResponse> semesters) {}
