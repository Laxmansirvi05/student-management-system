package com.campuscompanion.notice.dto;

import com.campuscompanion.notice.NoticeCategory;
import com.campuscompanion.notice.NoticeStatus;
import java.time.Instant;
import java.time.LocalDate;

public record NoticeResponse(
    Long id,
    String title,
    String body,
    NoticeCategory category,
    String targetBranch,
    Integer targetSemester,
    LocalDate dueDate,
    NoticeStatus status,
    Instant publishedAt) {}
