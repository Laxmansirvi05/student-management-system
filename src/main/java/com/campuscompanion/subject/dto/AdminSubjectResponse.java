package com.campuscompanion.subject.dto;

/** A subject as an admin sees it: the same fields, plus whether it is still in use. */
public record AdminSubjectResponse(
    Long id, String code, String name, String branch, int semester, int credits, boolean active) {}
