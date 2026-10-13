package com.campuscompanion.timetable.dto;

public record AdminTimetableEntryResponse(
    Long id,
    String branch,
    int semester,
    String section,
    int dayOfWeek,
    String startTime,
    String endTime,
    Long subjectId,
    String room,
    String facultyName) {}
