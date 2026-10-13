package com.campuscompanion.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Login identity settings. The student email domain is configuration, never code: a student's login
 * email is the roll number, an at sign, and this domain.
 */
@Validated
@ConfigurationProperties(prefix = "app.identity")
public record IdentityProperties(
    @NotBlank
        @Pattern(
            regexp = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$",
            message =
                "STUDENT_EMAIL_DOMAIN must be a lower-case host name such as university.example")
        String studentEmailDomain) {}
