package com.campuscompanion.student.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The personal details a student may change on their own profile. There is no field here for the
 * name, the roll number, the branch, the semester or the section: the college manages those, and a
 * request that tries to send one is refused as an unknown field. Every field is optional; a blank
 * one is saved as empty. The email and the links are checked further by ProfileLinks.
 */
public record OwnProfileUpdateRequest(
    @Size(max = 20, message = "Use at most 20 characters.")
        @Pattern(
            regexp = "^$|^[0-9+() -]{7,20}$",
            message = "Use 7 to 20 digits. Spaces, +, - and brackets are allowed.")
        String phone,
    @Size(max = 280, message = "Use at most 280 characters.") String bio,
    @Size(max = 254, message = "Use at most 254 characters.") String personalEmail,
    @Size(max = 200, message = "Use at most 200 characters.") String linkedinUrl,
    @Size(max = 200, message = "Use at most 200 characters.") String githubUrl,
    @Size(max = 200, message = "Use at most 200 characters.") String portfolioUrl,
    @Size(max = 200, message = "Use at most 200 characters.") String skills) {}
