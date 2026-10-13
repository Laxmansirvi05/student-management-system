package com.campuscompanion.common.config;

import com.campuscompanion.student.StudentProfileService;
import com.campuscompanion.student.StudentProvisioningService;
import com.campuscompanion.student.dto.StudentCreateRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Loads synthetic demo data when the application starts. It runs in the {@code dev} profile, or in
 * any profile when DEMO_SEED_ENABLED is true. Students are loaded only when no student exists, and
 * the academic records ({@link DemoAcademicSeeder}) only when no subject exists. It runs after the
 * first-admin bootstrap, because the academic records need an admin as their author.
 *
 * <p>Students are created through {@link StudentProvisioningService}, exactly like students an
 * admin provisions: the temporary password is the roll number and it must be changed at first
 * login. Every name and roll number here is invented. No real person's data may be added.
 */
@Component
@Order(2)
public class DevDataSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

  static final List<StudentCreateRequest> DEMO_STUDENTS =
      List.of(
          new StudentCreateRequest("23CSE001", "Demo Student One", "CSE", 3, "A", 2023, null),
          new StudentCreateRequest("23CSE002", "Demo Student Two", "CSE", 3, "A", 2023, null),
          new StudentCreateRequest("23CSE003", "Demo Student Three", "CSE", 3, "A", 2023, null),
          new StudentCreateRequest("23CSE004", "Demo Student Four", "CSE", 3, "B", 2023, null),
          new StudentCreateRequest("23ECE001", "Demo Student Five", "ECE", 3, "A", 2023, null),
          new StudentCreateRequest("22CSE001", "Demo Student Six", "CSE", 5, "A", 2022, null));

  private final SeedProperties properties;
  private final Environment environment;
  private final StudentProfileService studentProfiles;
  private final StudentProvisioningService provisioning;
  private final DemoAcademicSeeder academics;

  public DevDataSeeder(
      SeedProperties properties,
      Environment environment,
      StudentProfileService studentProfiles,
      StudentProvisioningService provisioning,
      DemoAcademicSeeder academics) {
    this.properties = properties;
    this.environment = environment;
    this.studentProfiles = studentProfiles;
    this.provisioning = provisioning;
    this.academics = academics;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!seedingRequested()) {
      return;
    }
    if (studentProfiles.anyStudentExists()) {
      log.info("Demo students were not loaded because student data already exists.");
    } else {
      DEMO_STUDENTS.forEach(provisioning::provision);
      log.info("Loaded synthetic demo data: {} students.", DEMO_STUDENTS.size());
    }
    academics.seedIfEmpty();
  }

  private boolean seedingRequested() {
    return properties.demoEnabled() || environment.acceptsProfiles(Profiles.of("dev"));
  }
}
