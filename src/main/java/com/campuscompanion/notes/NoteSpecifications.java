package com.campuscompanion.notes;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** The filters of the notes list, one Specification per view and filter. */
final class NoteSpecifications {

  private NoteSpecifications() {}

  /**
   * The views: the Trash shows only deleted notes, every other view hides them; Archived shows only
   * archived notes, every other view except the Trash hides them; Pinned shows pinned ones.
   */
  static Specification<Note> inView(Long studentId, String view) {
    return (root, query, cb) -> {
      List<Predicate> all = new ArrayList<>();
      all.add(cb.equal(root.get("studentId"), studentId));
      all.add(
          "trash".equals(view)
              ? cb.isNotNull(root.get("deletedAt"))
              : cb.isNull(root.get("deletedAt")));
      if ("archived".equals(view)) {
        all.add(cb.isTrue(root.get("archived")));
      } else if (!"trash".equals(view)) {
        all.add(cb.isFalse(root.get("archived")));
      }
      if ("pinned".equals(view)) {
        all.add(cb.isTrue(root.get("pinned")));
      }
      return cb.and(all.toArray(Predicate[]::new));
    };
  }

  /** A folder's ID, or the word "unfiled" for the notes that are in no folder. */
  static Specification<Note> inFolder(String folder) {
    return (root, query, cb) -> {
      if ("unfiled".equals(folder)) {
        return cb.isNull(root.get("folderId"));
      }
      try {
        return cb.equal(root.get("folderId"), Long.parseLong(folder));
      } catch (NumberFormatException notAnId) {
        return cb.disjunction();
      }
    };
  }

  static Specification<Note> withTag(String tag) {
    return (root, query, cb) ->
        cb.isTrue(cb.function("array_contains", Boolean.class, root.get("tags"), cb.literal(tag)));
  }

  /** A case-insensitive search of title and content. % _ and \ in the term are plain text. */
  static Specification<Note> matching(String term) {
    String pattern = "%" + escape(term.toLowerCase(java.util.Locale.ROOT)) + "%";
    return (root, query, cb) ->
        cb.or(
            cb.like(cb.lower(root.get("title")), pattern, '\\'),
            cb.like(cb.lower(root.get("content")), pattern, '\\'));
  }

  /** A case-insensitive search of the title only, for the link picker. */
  static Specification<Note> titleMatching(String term) {
    String pattern = "%" + escape(term.toLowerCase(java.util.Locale.ROOT)) + "%";
    return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern, '\\');
  }

  static Specification<Note> byIds(java.util.Collection<Long> ids) {
    return (root, query, cb) -> root.get("id").in(ids);
  }

  static String escape(String term) {
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
