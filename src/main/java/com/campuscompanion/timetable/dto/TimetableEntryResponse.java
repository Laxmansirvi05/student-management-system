package com.campuscompanion.timetable.dto;

/** One class as a student sees it. Times are written as "09:00". Day 1 is Monday. */
public record TimetableEntryResponse(
    int dayOfWeek,
    String startTime,
    String endTime,
    String subjectCode,
    String subjectName,
    String room,
    String facultyName) {}
