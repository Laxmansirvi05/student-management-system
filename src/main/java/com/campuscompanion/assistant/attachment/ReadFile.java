package com.campuscompanion.assistant.attachment;

import com.campuscompanion.ai.provider.AiImage;
import java.util.List;

/**
 * What was really read from an attached file: the picture itself (a photo or a screenshot), already
 * checked and redrawn. This is what the model is given, so a file that could not be read never
 * becomes one of these.
 */
public record ReadFile(String name, Kind kind, List<AiImage> pictures) {

  public enum Kind {
    IMAGE
  }

  public ReadFile {
    pictures = List.copyOf(pictures);
  }

  /** About how much memory the file takes while it is kept, in bytes. */
  public long weight() {
    long total = 0;
    for (AiImage picture : pictures) {
      total += picture.data().length;
    }
    return total;
  }
}
