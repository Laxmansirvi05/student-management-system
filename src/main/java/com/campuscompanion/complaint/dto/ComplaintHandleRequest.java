package com.campuscompanion.complaint.dto;

import com.campuscompanion.complaint.ComplaintStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The administrator's handling of a complaint: a status and an optional written response. */
public record ComplaintHandleRequest(
    @NotNull(message = "Choose a status.") ComplaintStatus status,
    @Size(max = 2000, message = "Use at most 2,000 characters.") String response) {}
