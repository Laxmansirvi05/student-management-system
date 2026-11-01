package com.campuscompanion.assistant;

import com.campuscompanion.ai.provider.AiImage;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.notes.NoteImageProcessor;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Decides whether a picture may go to the model with a question (Quick Assistant), and makes what
 * is sent. The picture is treated as hostile, as every upload is: it is at most 5 MB, it is a JPEG
 * or PNG by its declared type and by its first bytes, and the file itself is thrown away. A new
 * JPEG is drawn from its pixels, at most 1,568 pixels on the long side, turned upright, with
 * nothing hidden in the file, including the place and time a phone writes into its photos. The
 * picture is held only for the one request: it is not stored.
 */
@Component
public class AssistantImageChecker {

  public static final long MAX_BYTES = 5L * 1024 * 1024;
  static final int SIDE = 1568;

  private final NoteImageProcessor pictures;

  public AssistantImageChecker(NoteImageProcessor pictures) {
    this.pictures = pictures;
  }

  /** The picture to send for the uploaded bytes, or an error the student can read. */
  public AiImage prepare(byte[] content, String declaredType) {
    if (content == null || content.length == 0) {
      throw invalid("The picture is empty.");
    }
    if (content.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The picture is larger than 5 MB.");
    }
    String type = declaredType == null ? "" : declaredType.toLowerCase(Locale.ROOT).strip();
    String format =
        switch (type) {
          case "image/jpeg", "image/jpg" -> "jpeg";
          case "image/png" -> "png";
          default -> throw invalid("Attach a JPG or PNG picture.");
        };
    if (!matches(content, format)) {
      throw invalid("That file is not a " + format.toUpperCase(Locale.ROOT) + " picture.");
    }
    return new AiImage("image/jpeg", pictures.toJpeg(content, format, SIDE));
  }

  private static boolean matches(byte[] content, String format) {
    return "jpeg".equals(format)
        ? startsWith(content, (byte) 0xFF, (byte) 0xD8, (byte) 0xFF)
        : startsWith(
            content,
            (byte) 0x89,
            (byte) 'P',
            (byte) 'N',
            (byte) 'G',
            (byte) 0x0D,
            (byte) 0x0A,
            (byte) 0x1A,
            (byte) 0x0A);
  }

  private static boolean startsWith(byte[] content, byte... prefix) {
    if (content.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (content[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static ApiException invalid(String message) {
    return new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, message);
  }
}
