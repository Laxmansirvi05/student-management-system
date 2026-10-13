package com.campuscompanion.timetable.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TimetableWriteRequest(
    @NotBlank(message = "Enter the branch.") @Size(max = 20, message = "Use at most 20 characters.")
        String branch,
    @NotNull(message = "Choose a semester.")
        @Min(value = 1, message = "Semester must be 1 to 8.")
        @Max(value = 8, message = "Semester must be 1 to 8.")
        Integer semester,
    @NotBlank(message = "Enter the section.") @Size(max = 5, message = "Use at most 5 characters.")
        String section,
    @NotNull(message = "Choose a day.")
        @Min(value = 1, message = "Day must be 1 (Monday) to 7 (Sunday).")
        @Max(value = 7, message = "Day must be 1 (Monday) to 7 (Sunday).")
        Integer dayOfWeek,
    @NotBlank(message = "Enter the start time.")
        @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "Write the time like 09:00.")
        String startTime,
    @NotBlank(message = "Enter the end time.")
        @Pattern(regexp = "^\\d{2}:\\d{2}$", message = "Write the time like 09:50.")
        String endTime,
    @NotNull(message = "Choose a subject.") Long subjectId,
    @Size(max = 30, message = "Use at most 30 characters.") String room,
    @Size(max = 120, message = "Use at most 120 characters.") String facultyName) {}
