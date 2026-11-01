package com.campuscompanion.assistant.attachment;

/**
 * One piece of a document in reading order: some text, or a place where a picture belongs (picture
 * is the index in the file's own list of pictures, or -1 when this piece is only text).
 */
public record Segment(String text, int picture) {

  public static Segment text(String text) {
    return new Segment(text, -1);
  }

  public static Segment picture(String caption, int index) {
    return new Segment(caption, index);
  }

  public boolean hasPicture() {
    return picture >= 0;
  }
}
