package com.campuscompanion.notice.dto;

import java.time.Instant;

/** A short-lived link to a notice's official document, and whether it is a PDF or a picture. */
public record NoticeDocumentLinkResponse(String url, Instant expiresAt, String contentType) {}
