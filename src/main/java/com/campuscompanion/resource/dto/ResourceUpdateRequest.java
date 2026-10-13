package com.campuscompanion.resource.dto;

import com.campuscompanion.resource.ResourceStatus;
import com.campuscompanion.resource.ResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The details of a resource an admin may change afterwards, including hiding it. */
public record ResourceUpdateRequest(
    @NotBlank(message = "Enter a title.") @Size(max = 200, message = "Use at most 200 characters.")
        String title,
    @Size(max = 1000, message = "Use at most 1,000 characters.") String description,
    @NotNull(message = "Choose a subject.") Long subjectId,
    Long unitId,
    @NotNull(message = "Choose a type.") ResourceType type,
    @Valid QuestionPaperDetails questionPaper,
    @NotNull(message = "Choose a status.") ResourceStatus status) {

  public ResourceMetadata metadata() {
    return new ResourceMetadata(title, description, subjectId, unitId, type, questionPaper);
  }
}
