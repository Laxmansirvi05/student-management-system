package com.campuscompanion.subject;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

  Optional<Subject> findByCode(String code);

  List<Subject> findByBranchAndSemesterAndActiveTrueOrderByCode(String branch, int semester);
}
