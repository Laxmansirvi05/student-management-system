package com.campuscompanion.assistant.attachment;

import com.campuscompanion.ai.provider.AiImage;
import java.util.List;

/**
 * What was really read from an attached file: its text in reading order, with the places where a
 * picture belongs, and the pictures themselves (a photo, the scanned pages of a PDF, the figures of
 * a DOCX), already checked and redrawn. This is what the model is given, so a file that could not
 * be read never becomes one of these.
 *
 * @param pages the number of pages the file has (0 when the file has no pages, such as a photo)
 * @param pagesRead how many of them were read
 * @param notice something the student should know about how much was read, or null
 */
public record ReadFile(
    String name,
    Kind kind,
    int pages,
    int pagesRead,
    List<Segment> segments,
    List<AiImage> pictures,
    String notice) {

  public enum Kind {
    IMAGE,
    PDF,
    DOCX
  }

  public ReadFile {
    segments = List.copyOf(segments);
    pictures = List.copyOf(pictures);
  }

  /** The characters of text the file gave. */
  public int characters() {
    int total = 0;
    for (Segment segment : segments) {
      total += segment.text() == null ? 0 : segment.text().length();
    }
    return total;
  }

  /** About how much memory the file takes while it is kept, in bytes. */
  public long weight() {
    long total = characters() * 2L;
    for (AiImage picture : pictures) {
      total += picture.data().length;
    }
    return total;
  }
}
