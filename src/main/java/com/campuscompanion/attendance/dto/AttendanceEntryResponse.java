package com.campuscompanion.attendance.dto;

import com.campuscompanion.attendance.AttendanceStatus;
import java.time.LocalDate;

/** One class in a student's attendance history. */
public record AttendanceEntryResponse(LocalDate classDate, int periodNo, AttendanceStatus status) {}
