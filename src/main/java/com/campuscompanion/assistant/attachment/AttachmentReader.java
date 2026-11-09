package com.campuscompanion.assistant.attachment;

import com.campuscompanion.ai.provider.AiImage;
import com.campuscompanion.ai.provider.VisionProvider;
import com.campuscompanion.assistant.AssistantImageChecker;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Turns an attached picture into what the model can use. The kind is decided from the first bytes,
 * never from the name or the type the browser claims: a JPEG or a PNG is checked and redrawn as a
 * JPEG (no metadata, upright, at most 1,568 pixels) and goes to a model that can see. Anything else
 * is refused with a reason (PDF and Word reading was dropped: it used too many tokens).
 */
@Component
public class AttachmentReader {

  public static final long MAX_BYTES = AssistantImageChecker.MAX_BYTES;

  private final AssistantImageChecker images;
  private final VisionProvider vision;

  public AttachmentReader(AssistantImageChecker images, VisionProvider vision) {
    this.images = images;
    this.vision = vision;
  }

  /** Reads one file, or throws an error the student can read. */
  public ReadFile read(String fileName, byte[] file) {
    if (file == null || file.length == 0) {
      throw invalid("The file is empty.");
    }
    if (file.length > MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The picture is larger than 5 MB.");
    }
    String type = typeOf(file);
    if (type == null) {
      throw invalid(
          startsWith(file, 'R', 'I', 'F', 'F')
              ? "WEBP pictures are converted by the page before they are attached. Try again, or attach a JPG or PNG."
              : "Attach a JPG, PNG or WEBP picture.");
    }
    if (!vision.available()) {
      throw new ApiException(
          ErrorCode.AI_UNAVAILABLE, "Reading pictures is not set up on this server yet.");
    }
    AiImage image = images.prepare(file, type);
    return new ReadFile(cleanName(fileName), ReadFile.Kind.IMAGE, List.of(image));
  }

  private static String typeOf(byte[] file) {
    if (startsWith(file, 0xFF, 0xD8, 0xFF)) {
      return "image/jpeg";
    }
    if (startsWith(file, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
      return "image/png";
    }
    return null;
  }

  private static boolean startsWith(byte[] file, int... prefix) {
    if (file.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((file[i] & 0xFF) != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  /** A name safe to show and to put in a prompt: no path, no control characters, no brackets. */
  static String cleanName(String fileName) {
    String name = fileName == null ? "" : fileName;
    name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    name = name.replaceAll("[\\p{Cntrl}\\[\\]<>\"]", " ").replaceAll("\\s+", " ").strip();
    if (name.length() > 80) {
      name = name.substring(0, 80).strip();
    }
    return name.isEmpty() ? "attachment" : name;
  }

  private static ApiException invalid(String message) {
    return new ApiException(ErrorCode.UPLOAD_INVALID_TYPE, message);
  }
}
