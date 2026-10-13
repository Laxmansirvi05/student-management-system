package com.campuscompanion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.auth.Role;
import com.campuscompanion.auth.User;
import com.campuscompanion.auth.UserRepository;
import com.campuscompanion.student.StudentProfile;
import com.campuscompanion.student.StudentProfileRepository;
import com.campuscompanion.subject.Subject;
import com.campuscompanion.subject.SubjectRepository;
import com.campuscompanion.subject.SubjectUnit;
import com.campuscompanion.subject.SubjectUnitRepository;
import com.campuscompanion.support.PostgresIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Task T1-04: the entities map onto the migrated schema (Hibernate validates this at startup) and
 * the repositories work against real PostgreSQL as the runtime role. All data is synthetic.
 */
class CoreRepositoriesIT extends PostgresIntegrationTest {

  @Autowired private UserRepository users;
  @Autowired private StudentProfileRepository profiles;
  @Autowired private SubjectRepository subjects;
  @Autowired private SubjectUnitRepository units;

  @Test
  void savesAUserWithItsDefaultsAndFindsItByLoginEmail() {
    User saved =
        users.save(
            new User("repo.user1@university.example", "hash", Role.STUDENT, "Demo Student One"));

    User found = users.findByLoginEmail("repo.user1@university.example").orElseThrow();

    assertThat(found.getId()).isEqualTo(saved.getId()).isNotNull();
    assertThat(found.getRole()).isEqualTo(Role.STUDENT);
    assertThat(found.isActive()).isTrue();
    assertThat(found.isMustChangePassword()).isTrue();
    assertThat(found.getFailedLoginCount()).isZero();
    assertThat(found.getLockedUntil()).isNull();
    assertThat(found.getLastLoginAt()).isNull();
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(users.existsByLoginEmail("repo.user1@university.example")).isTrue();
    assertThat(users.existsByLoginEmail("nobody@university.example")).isFalse();
    assertThat(users.findByLoginEmail("nobody@university.example")).isEmpty();
  }

  @Test
  void rejectsASecondUserWithTheSameLoginEmail() {
    users.save(new User("repo.duplicate@university.example", "hash", Role.STUDENT, "First"));

    assertThatThrownBy(
            () ->
                users.saveAndFlush(
                    new User("repo.duplicate@university.example", "hash", Role.STUDENT, "Second")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void linksAStudentProfileToItsAccount() {
    User account =
        users.save(
            new User("repo23cse001@university.example", "hash", Role.STUDENT, "Demo Student Two"));
    profiles.save(
        new StudentProfile(account, "REPO23CSE001", "Demo Student Two", "CSE", 3, "A", 2023, null));

    StudentProfile found = profiles.findByUserId(account.getId()).orElseThrow();

    assertThat(found.getRollNo()).isEqualTo("REPO23CSE001");
    assertThat(found.getCurrentSemester()).isEqualTo(3);
    assertThat(found.getAdmissionYear()).isEqualTo(2023);
    assertThat(found.getPhone()).isNull();
    assertThat(profiles.existsByRollNo("REPO23CSE001")).isTrue();
    assertThat(profiles.existsByRollNo("REPO23CSE999")).isFalse();
  }

  @Test
  void rejectsASecondProfileWithTheSameRollNumber() {
    User first = users.save(new User("repo.roll.a@university.example", "hash", Role.STUDENT, "A"));
    User second = users.save(new User("repo.roll.b@university.example", "hash", Role.STUDENT, "B"));
    profiles.save(new StudentProfile(first, "REPOROLL01", "A", "CSE", 3, "A", 2023, null));

    assertThatThrownBy(
            () ->
                profiles.saveAndFlush(
                    new StudentProfile(second, "REPOROLL01", "B", "CSE", 3, "A", 2023, null)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void listsActiveSubjectsOfABranchAndSemesterAndTheirUnitsInOrder() {
    Subject dbms =
        subjects.save(new Subject("REPO301", "Database Management Systems", "REPO", 3, 4));
    subjects.save(new Subject("REPO302", "Operating Systems", "REPO", 3, 4));
    subjects.save(new Subject("REPO401", "Computer Networks", "REPO", 4, 3));
    units.save(new SubjectUnit(dbms, 2, "Relational Model"));
    units.save(new SubjectUnit(dbms, 1, "Introduction"));

    List<Subject> thirdSemester =
        subjects.findByBranchAndSemesterAndActiveTrueOrderByCode("REPO", 3);
    List<SubjectUnit> dbmsUnits = units.findBySubjectIdOrderByUnitNo(dbms.getId());

    assertThat(thirdSemester).extracting(Subject::getCode).containsExactly("REPO301", "REPO302");
    assertThat(subjects.findByCode("REPO401")).isPresent();
    assertThat(dbmsUnits).extracting(SubjectUnit::getUnitNo).containsExactly(1, 2);
    assertThat(dbmsUnits)
        .extracting(SubjectUnit::getTitle)
        .containsExactly("Introduction", "Relational Model");
  }

  @Test
  void rejectsTwoUnitsWithTheSameNumberInOneSubject() {
    Subject subject = subjects.save(new Subject("REPO303", "Discrete Mathematics", "REPO", 3, 4));
    units.save(new SubjectUnit(subject, 1, "Sets"));

    assertThatThrownBy(() -> units.saveAndFlush(new SubjectUnit(subject, 1, "Sets again")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }
}
