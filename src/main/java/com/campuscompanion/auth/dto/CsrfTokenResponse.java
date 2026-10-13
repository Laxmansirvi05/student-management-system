package com.campuscompanion.auth.dto;

/** The CSRF token and the name of the header the frontend must send it in. */
public record CsrfTokenResponse(String headerName, String token) {}
