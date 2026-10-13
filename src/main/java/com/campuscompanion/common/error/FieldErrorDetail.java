package com.campuscompanion.common.error;

/** One invalid field in a request, as returned in the {@code errors} list of an error response. */
public record FieldErrorDetail(String field, String message) {}
