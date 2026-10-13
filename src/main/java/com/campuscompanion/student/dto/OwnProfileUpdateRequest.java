package com.campuscompanion.student.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The personal details a student may change on their own profile. There is no field here for the
 * name, the roll number, the branch, the semester or the section: the college manages those, and a
 * request that tries to send one is refused as an unknown field.
 */
public record OwnProfileUpdateRequest(
    @Size(max = 20, message = "Use at most 20 characters.")
        @Pattern(
            regexp = "^$|^[0-9+() -]{7,20}$",
            message = "Use 7 to 20 digits. Spaces, +, - and brackets are allowed.")
        String phone,
    @Size(max = 280, message = "Use at most 280 characters.") String bio) {}
