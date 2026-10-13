package com.campuscompanion.ai.dto;

import java.util.List;

public record SummarizeResponse(
    String summary,
    List<String> keyPoints,
    List<Definition> definitions,
    AiBasis basis,
    String notice,
    String promptVersion) {

  public record Definition(String term, String meaning) {}
}
