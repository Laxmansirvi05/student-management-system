package com.campuscompanion.pyq;

import com.campuscompanion.pyq.dto.PyqPaperResponse;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Turns papers into what the pages show. */
@Component
class PyqAssembler {

  List<PyqPaperResponse> toResponses(List<PyqPaper> papers, Set<Long> saved) {
    return papers.stream().map(paper -> toResponse(paper, saved)).toList();
  }

  PyqPaperResponse toResponse(PyqPaper paper, Collection<Long> saved) {
    return new PyqPaperResponse(
        paper.getId(),
        paper.getBranch(),
        paper.getSubjectName(),
        paper.getExamYear(),
        paper.getExamMonth(),
        monthName(paper.getExamMonth()),
        paper.getExamKind(),
        paper.getMidNumber(),
        paper.getRegulation(),
        paper.getCourseCode(),
        paper.getVariant(),
        paper.getSizeBytes(),
        paper.getDownloadCount(),
        saved.contains(paper.getId()),
        paper.getStatus().name(),
        paper.getCreatedAt(),
        downloadName(paper));
  }

  static String monthName(int month) {
    return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
  }

  /**
   * The name a saved file gets: "CSE - Machine Learning - 2025 December Supplementary AR20.pdf".
   */
  static String downloadName(PyqPaper paper) {
    String kind =
        switch (paper.getExamKind()) {
          case REGULAR -> "Regular";
          case SUPPLY -> "Supplementary";
          case MID -> paper.getMidNumber() == null ? "Mid" : "Mid " + paper.getMidNumber();
        };
    StringBuilder name =
        new StringBuilder(paper.getBranch())
            .append(" - ")
            .append(paper.getSubjectName())
            .append(" - ")
            .append(paper.getExamYear())
            .append(' ')
            .append(monthName(paper.getExamMonth()))
            .append(' ')
            .append(kind);
    if (paper.getRegulation() != null) {
      name.append(' ').append(paper.getRegulation());
    }
    if (paper.getVariant() > 1) {
      name.append(" (set ").append(paper.getVariant()).append(')');
    }
    return name.toString().replaceAll("[\\\\/:*?\"<>|]+", "").replaceAll("\\s+", " ") + ".pdf";
  }
}
