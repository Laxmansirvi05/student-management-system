package com.campuscompanion.notice.dto;

import java.time.LocalDate;

public record ExamPaperResponse(LocalDate examDate, String subject) {}
