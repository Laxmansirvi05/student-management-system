package com.campuscompanion.assistant.attachment;

import com.campuscompanion.ai.provider.AiImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Puts the pictures of a question together for the model: a list in the prompt ("Picture 2: name")
 * and the pictures themselves in the same order, so the model can tell which is which. At most
 * {@link #MAX_PICTURES} go with one question (some providers refuse more than five); the text says
 * when one was left out.
 */
public final class DocumentContext {

  public static final int MAX_PICTURES = 5;

  /** The text for the model, the pictures to send with it, and whether any were left out. */
  public record Built(String text, List<AiImage> pictures, boolean shortened) {

    public boolean isEmpty() {
      return text.isEmpty() && pictures.isEmpty();
    }
  }

  private DocumentContext() {}

  public static Built build(List<ReadFile> files) {
    StringBuilder text = new StringBuilder();
    List<AiImage> pictures = new ArrayList<>();
    boolean shortened = false;
    for (ReadFile file : files) {
      for (AiImage picture : file.pictures()) {
        if (pictures.size() < MAX_PICTURES) {
          pictures.add(picture);
          text.append("Picture ")
              .append(pictures.size())
              .append(": \"")
              .append(file.name())
              .append("\"\n");
        } else {
          shortened = true;
        }
      }
    }
    if (shortened) {
      text.append("[More pictures were attached but only ")
          .append(MAX_PICTURES)
          .append(" can be sent with one question. Say that you have not seen the rest.]\n");
    }
    return new Built(text.toString().strip(), pictures, shortened);
  }
}
