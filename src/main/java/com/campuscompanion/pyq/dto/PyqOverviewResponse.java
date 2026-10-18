package com.campuscompanion.pyq.dto;

import java.util.List;

/** What the PYQ page needs to draw its filters: the branches, the years and the regulations. */
public record PyqOverviewResponse(
    List<BranchCount> branches, List<Integer> years, List<String> regulations, long totalPapers) {

  public record BranchCount(String branch, long subjects, long papers) {}
}
