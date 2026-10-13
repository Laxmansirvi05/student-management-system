package com.campuscompanion.resource.dto;

import com.campuscompanion.resource.ResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The details an admin gives when uploading a PDF. */
public record ResourceMetadata(
    @NotBlank(message = "Enter a title.") @Size(max = 200, message = "Use at most 200 characters.")
        String title,
    @Size(max = 1000, message = "Use at most 1,000 characters.") String description,
    @NotNull(message = "Choose a subject.") Long subjectId,
    Long unitId,
    @NotNull(message = "Choose a type.") ResourceType type,
    @Valid QuestionPaperDetails questionPaper) {}
