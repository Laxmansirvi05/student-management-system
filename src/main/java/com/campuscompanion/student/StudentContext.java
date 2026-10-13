package com.campuscompanion.student;

/**
 * The logged-in student, as other features need to know them: the student ID that owns their
 * records, and the class they are in. It is always built from the session's account ID on the
 * server and never from anything the browser sends.
 */
public record StudentContext(
    Long studentId,
    String rollNo,
    String fullName,
    String branch,
    int currentSemester,
    String section) {}
