package com.campuscompanion.complaint.dto;

import com.campuscompanion.complaint.ComplaintCategory;
import com.campuscompanion.complaint.ComplaintType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A new complaint. The student is never in the request: the server knows who is logged in. */
public record ComplaintRequest(
    @NotNull(message = "Choose public, private or secure.") ComplaintType type,
    @NotNull(message = "Choose a category.") ComplaintCategory category,
    @NotBlank(message = "Give the complaint a short title.")
        @Size(min = 5, max = 120, message = "Use 5 to 120 characters.")
        String title,
    @NotBlank(message = "Describe the problem.")
        @Size(min = 20, max = 2000, message = "Use 20 to 2,000 characters.")
        String description,
    /** For a public complaint: show the roll number to other students. Ignored otherwise. */
    Boolean showIdentity) {}
