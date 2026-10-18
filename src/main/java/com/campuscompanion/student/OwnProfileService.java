package com.campuscompanion.student;

import com.campuscompanion.audit.AuditAction;
import com.campuscompanion.audit.AuditService;
import com.campuscompanion.audit.AuditSnapshot;
import com.campuscompanion.common.error.FieldErrorDetail;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.storage.StorageUnavailableException;
import com.campuscompanion.student.dto.OwnProfileUpdateRequest;
import com.campuscompanion.student.dto.PhotoUrlResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a student may change on their own profile (FR-STU-003): the phone number, a short text about
 * themselves, and a photo. Whose profile it is always comes from the session; no method here takes
 * a student ID. The name, roll number, branch, semester and section stay with the college.
 *
 * <p>The photo is kept in file storage, never in the database and never on this server, and is
 * shown through a link that lasts five minutes.
 */
@Service
public class OwnProfileService {

  static final Duration LINK_LIFETIME = Duration.ofMinutes(5);
  private static final String STUDENT = "STUDENT";

  private static final Logger log = LoggerFactory.getLogger(OwnProfileService.class);

  private final StudentProfileRepository profiles;
  private final ProfilePhotoProcessor photos;
  private final StorageClient storage;
  private final AuditService audit;

  public OwnProfileService(
      StudentProfileRepository profiles,
      ProfilePhotoProcessor photos,
      StorageClient storage,
      AuditService audit) {
    this.profiles = profiles;
    this.photos = photos;
    this.storage = storage;
    this.audit = audit;
  }

  /**
   * Saves the student's own details: phone, text, personal email, links and skills. A changed phone
   * number is written to the audit log.
   */
  @Transactional
  public void update(CurrentUser currentUser, OwnProfileUpdateRequest request) {
    StudentProfile profile = own(currentUser);
    AuditSnapshot.Profile before = snapshot(profile);
    List<FieldErrorDetail> problems = new ArrayList<>();
    StudentProfile.OwnDetails details =
        new StudentProfile.OwnDetails(
            blankToNull(request.phone()),
            blankToNull(request.bio()),
            ProfileLinks.email(request.personalEmail(), "personalEmail", problems),
            ProfileLinks.link(request.linkedinUrl(), "linkedinUrl", "linkedin.com", problems),
            ProfileLinks.link(request.githubUrl(), "githubUrl", "github.com", problems),
            ProfileLinks.link(request.portfolioUrl(), "portfolioUrl", null, problems),
            blankToNull(request.skills()));
    if (!problems.isEmpty()) {
      throw new ValidationException(problems);
    }
    profile.updateOwnDetails(details);
    AuditSnapshot.Profile after = snapshot(profile);
    if (!after.equals(before)) {
      audit.record(AuditAction.PROFILE_UPDATED, STUDENT, profile.getId(), before, after);
    }
  }

  /**
   * Stores a new photo. The upload is checked and redrawn first; what is stored is the redrawn
   * picture. If the profile cannot be saved, the stored picture is removed again. The old picture
   * is removed last, so a failure on the way never leaves the student without one.
   */
  @Transactional
  public void setPhoto(CurrentUser currentUser, byte[] content, String declaredType) {
    StudentProfile profile = own(currentUser);
    byte[] picture = photos.process(content, declaredType);
    // The path is a random ID. Nothing of the file name or of the student is in it.
    String path = "profile-photos/" + UUID.randomUUID() + ".jpg";
    String oldPath = profile.getPhotoPath();

    storage.upload(path, picture, "image/jpeg");
    try {
      profile.setPhotoPath(path);
      profiles.saveAndFlush(profile);
    } catch (RuntimeException databaseFailure) {
      removeQuietly(path);
      throw databaseFailure;
    }
    if (oldPath != null) {
      removeQuietly(oldPath);
    }
  }

  /** Removes the photo. Removing when there is none has no effect. */
  @Transactional
  public void removePhoto(CurrentUser currentUser) {
    StudentProfile profile = own(currentUser);
    String path = profile.getPhotoPath();
    if (path == null) {
      return;
    }
    profile.setPhotoPath(null);
    profiles.saveAndFlush(profile);
    // If the file cannot be removed now, the exception undoes the change, so the two stay together.
    storage.delete(path);
  }

  /** A link to the student's own photo. "Not found" when they have none. */
  @Transactional(readOnly = true)
  public PhotoUrlResponse photoUrl(CurrentUser currentUser) {
    String path = own(currentUser).getPhotoPath();
    if (path == null) {
      throw new ResourceNotFoundException();
    }
    StorageClient.SignedLink link = storage.signedUrl(path, LINK_LIFETIME, null);
    return new PhotoUrlResponse(link.url(), link.expiresAt());
  }

  private StudentProfile own(CurrentUser currentUser) {
    return profiles.findByUserId(currentUser.id()).orElseThrow(ResourceNotFoundException::new);
  }

  private void removeQuietly(String path) {
    try {
      storage.delete(path);
    } catch (StorageUnavailableException stillThere) {
      log.error("A stored photo was left without a profile and needs cleaning up.");
    }
  }

  private static AuditSnapshot.Profile snapshot(StudentProfile profile) {
    return new AuditSnapshot.Profile(
        profile.getFullName(),
        profile.getBranch(),
        profile.getCurrentSemester(),
        profile.getSection(),
        profile.getAdmissionYear(),
        profile.getPhone());
  }

  private static String blankToNull(String text) {
    return text == null || text.isBlank() ? null : text.strip();
  }
}
