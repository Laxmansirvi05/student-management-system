package com.campuscompanion.student;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.dto.DashboardResponse;
import com.campuscompanion.student.dto.OwnProfileUpdateRequest;
import com.campuscompanion.student.dto.PhotoUrlResponse;
import com.campuscompanion.student.dto.StudentProfileResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * A student's own details. The contract is in docs/09-api-contract.md. The path ends in "/me" and
 * takes no student identifier: the student is always the one who is logged in.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

  private final StudentProfileService studentProfiles;
  private final OwnProfileService ownProfile;
  private final DashboardService dashboard;

  public StudentController(
      StudentProfileService studentProfiles,
      OwnProfileService ownProfile,
      DashboardService dashboard) {
    this.studentProfiles = studentProfiles;
    this.ownProfile = ownProfile;
    this.dashboard = dashboard;
  }

  @GetMapping("/me")
  public StudentProfileResponse ownProfile(@AuthenticationPrincipal CurrentUser currentUser) {
    return studentProfiles.ownProfile(currentUser);
  }

  /** Saves the personal details a student may edit. The body has no field for anything else. */
  @PutMapping("/me")
  public StudentProfileResponse updateOwnProfile(
      @AuthenticationPrincipal CurrentUser currentUser,
      @Valid @RequestBody OwnProfileUpdateRequest request) {
    ownProfile.update(currentUser, request);
    return studentProfiles.ownProfile(currentUser);
  }

  /** One part, "file": a JPEG or PNG photo of at most 2 MB. */
  @PutMapping(path = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public StudentProfileResponse setPhoto(
      @AuthenticationPrincipal CurrentUser currentUser, @RequestPart("file") MultipartFile file)
      throws IOException {
    if (file.getSize() > ProfilePhotoProcessor.MAX_BYTES) {
      // Refused by its size alone, before the file is read into memory.
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The photo is larger than 2 MB.");
    }
    ownProfile.setPhoto(currentUser, file.getBytes(), file.getContentType());
    return studentProfiles.ownProfile(currentUser);
  }

  @DeleteMapping("/me/photo")
  public ResponseEntity<Void> removePhoto(@AuthenticationPrincipal CurrentUser currentUser) {
    ownProfile.removePhoto(currentUser);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me/photo-url")
  public PhotoUrlResponse photoUrl(@AuthenticationPrincipal CurrentUser currentUser) {
    return ownProfile.photoUrl(currentUser);
  }

  @GetMapping("/me/dashboard")
  public DashboardResponse dashboard(@AuthenticationPrincipal CurrentUser currentUser) {
    return dashboard.dashboardFor(currentUser);
  }
}
