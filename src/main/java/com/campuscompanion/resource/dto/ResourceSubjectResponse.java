package com.campuscompanion.resource.dto;

/** A subject "folder" of the resource hub: the subject and how many published resources it has. */
public record ResourceSubjectResponse(
    Long subjectId, String subjectCode, String subjectName, int semester, long resources) {}
