package com.campuscompanion.pyq.dto;

import java.time.Instant;

/** A link to open or save one paper. It stops working at expiresAt. */
public record PyqLinkResponse(String url, Instant expiresAt) {}
