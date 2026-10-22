package com.campuscompanion.notes.dto;

/**
 * The answer to saving a note: the note, or {@code deleted: true} when an edit left it empty and it
 * was therefore removed instead of saved.
 */
public record NoteSaved(NoteResponse note, Boolean deleted) {

  public static NoteSaved of(NoteResponse note) {
    return new NoteSaved(note, null);
  }

  public static NoteSaved removed() {
    return new NoteSaved(null, Boolean.TRUE);
  }
}
