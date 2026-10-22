package com.campuscompanion.notes;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the notes feature. {@code extraFileTypes} lists the extensions of documents that may
 * be attached besides PDF (doc, docx, xls, xlsx, ppt, pptx, txt, csv, md, zip). It is empty by
 * default because the private storage bucket accepts only PDF and JPEG; when the bucket's allowed
 * types are widened, the same types are listed here (NOTES_EXTRA_FILE_TYPES).
 */
@ConfigurationProperties(prefix = "app.notes")
public record NotesProperties(List<String> extraFileTypes) {

  public NotesProperties {
    extraFileTypes = extraFileTypes == null ? List.of() : List.copyOf(extraFileTypes);
  }
}
