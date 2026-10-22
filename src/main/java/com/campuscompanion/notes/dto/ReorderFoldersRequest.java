package com.campuscompanion.notes.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The new order of folders after a drag. One request, because every folder after it shifts. */
public record ReorderFoldersRequest(@NotEmpty @Size(max = 500) List<Entry> order) {

  /** {@code setParent} says the folder's parent changed too; a null parent then means top level. */
  public record Entry(
      @NotNull Long id, @NotNull Integer position, Long parentId, Boolean setParent) {}
}
