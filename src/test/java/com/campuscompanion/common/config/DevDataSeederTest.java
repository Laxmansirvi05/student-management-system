package com.campuscompanion.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.student.StudentProvisioningService;
import com.campuscompanion.student.StudentRosterValidator;
import com.campuscompanion.student.dto.StudentCreateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** Task T1-10: when the demo seeder runs, and that its data is valid and plainly invented. */
class DevDataSeederTest {

  private final StudentProfileService studentProfiles = mock(StudentProfileService.class);
  private final StudentProvisioningService provisioning = mock(StudentProvisioningService.class);

  @Test
  void doesNothingOutsideTheDevProfileUnlessItIsSwitchedOn() {
    seeder(false).run(null);
    seeder(false, "prod").run(null);
    seeder(false, "test").run(null);

    verify(provisioning, never()).provision(any());
  }

  @Test
  void runsInTheDevProfile() {
    seeder(false, "dev").run(null);

    verify(provisioning, times(DevDataSeeder.DEMO_STUDENTS.size())).provision(any());
  }

  @Test
  void runsInAnyProfileWhenItIsSwitchedOn() {
    seeder(true, "prod").run(null);

    verify(provisioning, times(DevDataSeeder.DEMO_STUDENTS.size())).provision(any());
  }

  @Test
  void doesNothingWhenStudentDataAlreadyExists() {
    when(studentProfiles.anyStudentExists()).thenReturn(true);

    seeder(true, "dev").run(null);

    verify(provisioning, never()).provision(any());
  }

  @Test
  void everyDemoStudentIsValidUniqueAndPlainlyInvented() {
    StudentRosterValidator validator = new StudentRosterValidator();

    assertThat(DevDataSeeder.DEMO_STUDENTS)
        .allSatisfy(
            student -> {
              assertThat(validator.violations(student)).isEmpty();
              assertThat(student.fullName()).startsWith("Demo Student ");
              assertThat(student.phone()).isNull();
            });
    assertThat(DevDataSeeder.DEMO_STUDENTS)
        .extracting(StudentCreateRequest::rollNo)
        .doesNotHaveDuplicates();
  }

  private DevDataSeeder seeder(boolean demoEnabled, String... activeProfiles) {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles(activeProfiles);
    return new DevDataSeeder(
        new SeedProperties(demoEnabled), environment, studentProfiles, provisioning);
  }
}
