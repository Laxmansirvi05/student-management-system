package com.campuscompanion.notes;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteFolderRepository extends JpaRepository<NoteFolder, Long> {

  List<NoteFolder> findByStudentIdOrderByPositionAscIdAsc(Long studentId);

  Optional<NoteFolder> findByIdAndStudentId(Long id, Long studentId);

  Optional<NoteFolder> findByStudentIdAndName(Long studentId, String name);

  boolean existsByStudentIdAndNameAndIdNot(Long studentId, String name, Long id);

  @Query("select max(f.position) from NoteFolder f where f.studentId = :studentId")
  Optional<Integer> highestPosition(@Param("studentId") Long studentId);
}
