package com.campuscompanion.assistant.attachment;

import com.campuscompanion.ai.provider.AiImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts the files of a question together for the model: the text of each file in reading order, with
 * a marker where a picture belongs ("Picture 2"), and the pictures themselves in the same order, so
 * the model can tell which picture a marker means. The text is limited, because a model takes only
 * so much at a time and the free plans of the providers take less; when a document is cut, the text
 * says so and the model is told to say so too.
 */
public final class DocumentContext {

  /** The most characters of document text sent with one question, all files together. */
  public static final int MAX_CHARS = 30_000;

  /** The most pictures sent with one question (some providers refuse more than five). */
  public static final int MAX_PICTURES = 5;

  private static final int MIN_PER_FILE = 4_000;

  /** The text for the model, the pictures to send with it, and what had to be left out. */
  public record Built(String text, List<AiImage> pictures, boolean shortened) {

    public boolean isEmpty() {
      return text.isEmpty() && pictures.isEmpty();
    }
  }

  private DocumentContext() {}

  public static Built build(List<ReadFile> files) {
    if (files.isEmpty()) {
      return new Built("", List.of(), false);
    }
    int perFile = Math.max(MIN_PER_FILE, MAX_CHARS / files.size());
    StringBuilder text = new StringBuilder();
    List<AiImage> pictures = new ArrayList<>();
    boolean shortened = false;
    int number = 0;
    for (ReadFile file : files) {
      number++;
      text.append("Document ")
          .append(number)
          .append(": \"")
          .append(file.name())
          .append("\" (")
          .append(describe(file))
          .append(")\n");
      if (file.notice() != null) {
        text.append("[Note: ").append(file.notice()).append("]\n");
      }
      int used = 0;
      boolean cut = false;
      for (Segment segment : file.segments()) {
        if (segment.hasPicture()) {
          if (pictures.size() < MAX_PICTURES) {
            pictures.add(file.pictures().get(segment.picture()));
            text.append("[Picture ").append(pictures.size()).append(": ");
            text.append(segment.text().isBlank() ? "a picture in this document" : segment.text());
            text.append("]\n");
          } else {
            text.append("[A picture here is not shown: only ")
                .append(MAX_PICTURES)
                .append(" pictures can be sent with one question.]\n");
            shortened = true;
          }
        } else if (!cut) {
          String part = segment.text();
          if (used + part.length() > perFile) {
            part = part.substring(0, Math.max(0, perFile - used));
            cut = true;
          }
          used += part.length();
          text.append(part).append('\n');
        }
      }
      if (cut) {
        text.append("[The rest of this document is not included here because it is long. ")
            .append("Say that you have not seen the rest.]\n");
        shortened = true;
      }
      text.append('\n');
    }
    return new Built(text.toString().strip(), pictures, shortened);
  }

  private static String describe(ReadFile file) {
    return switch (file.kind()) {
      case IMAGE -> "a picture";
      case PDF -> "PDF, " + file.pages() + (file.pages() == 1 ? " page" : " pages");
      case DOCX -> "Word document";
    };
  }
}
