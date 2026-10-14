package com.campuscompanion.student;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.student.dto.StudentImportResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin: create many students from one CSV file. Contract: docs/09-api-contract.md section 8.14.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/imports")
public class AdminImportController {

  private final StudentImportService imports;

  public AdminImportController(StudentImportService imports) {
    this.imports = imports;
  }

  /** One part, "file": the CSV, at most 1 MB. */
  @PostMapping(path = "/students", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public StudentImportResponse students(@RequestPart("file") MultipartFile file)
      throws IOException {
    if (file.getSize() > StudentImportService.MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The file is larger than 1 MB.");
    }
    return imports.importStudents(file.getBytes(), file.getOriginalFilename());
  }
}
