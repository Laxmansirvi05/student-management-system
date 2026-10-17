package com.campuscompanion.attendance.dto;

/** What an attendance report import did: new reports, corrected ones, and ones already the same. */
public record AttendanceReportImportResponse(int created, int corrected, int unchanged) {}
