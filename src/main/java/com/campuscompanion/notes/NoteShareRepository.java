package com.campuscompanion.notes;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteShareRepository extends JpaRepository<NoteShare, Long> {

  Optional<NoteShare> findByNoteId(Long noteId);

  Optional<NoteShare> findBySlug(String slug);

  List<NoteShare> findByNoteIdIn(Collection<Long> noteIds);
}
