package com.campuscompanion.subject.dto;

/** A subject as students see it. Other features also use this record to name a subject. */
public record SubjectResponse(
    Long id, String code, String name, String branch, int semester, int credits) {}
