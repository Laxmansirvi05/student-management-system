package com.campuscompanion.notes;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NoteShareRecipientRepository extends JpaRepository<NoteShareRecipient, Long> {

  List<NoteShareRecipient> findByNoteIdOrderByIdAsc(Long noteId);

  List<NoteShareRecipient> findByRecipientId(Long recipientId);

  Optional<NoteShareRecipient> findByNoteIdAndRecipientId(Long noteId, Long recipientId);

  List<NoteShareRecipient> findByNoteIdIn(Collection<Long> noteIds);

  void deleteByNoteId(Long noteId);
}
