package com.campuscompanion.notes.dto;

/**
 * A classmate a note is shared with, by roll number only. No name: the owner already knows the roll
 * number they typed, and a name would turn sharing into a directory of every student.
 */
public record ShareRecipientResponse(Long id, Long recipientId, String rollNo, String permission) {}
