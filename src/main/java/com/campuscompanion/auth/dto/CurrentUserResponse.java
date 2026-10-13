package com.campuscompanion.auth.dto;

import com.campuscompanion.auth.Role;

/** The logged-in account, as returned by login and by {@code GET /auth/me}. */
public record CurrentUserResponse(
    String email, String displayName, Role role, boolean mustChangePassword) {}
