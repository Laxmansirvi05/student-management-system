package com.campuscompanion.coding.dto;

import com.campuscompanion.coding.platform.CodingPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Link an account: the site, and the username or a link to the profile. */
public record CodingAccountRequest(
    @NotNull(message = "Choose a site.") CodingPlatform platform,
    @NotBlank(message = "Enter your username.")
        @Size(max = 200, message = "Enter at most 200 characters.")
        String handle) {}
