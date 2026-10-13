package com.campuscompanion.student;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.dto.DashboardResponse;
import com.campuscompanion.student.dto.StudentProfileResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A student's own details. The contract is in docs/09-api-contract.md. The path ends in "/me" and
 * takes no student identifier: the student is always the one who is logged in.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

  private final StudentProfileService studentProfiles;
  private final DashboardService dashboard;

  public StudentController(StudentProfileService studentProfiles, DashboardService dashboard) {
    this.studentProfiles = studentProfiles;
    this.dashboard = dashboard;
  }

  @GetMapping("/me")
  public StudentProfileResponse ownProfile(@AuthenticationPrincipal CurrentUser currentUser) {
    return studentProfiles.ownProfile(currentUser);
  }

  @GetMapping("/me/dashboard")
  public DashboardResponse dashboard(@AuthenticationPrincipal CurrentUser currentUser) {
    return dashboard.dashboardFor(currentUser);
  }
}
