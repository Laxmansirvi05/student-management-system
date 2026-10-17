package com.campuscompanion.attendance;

import com.campuscompanion.attendance.dto.AttendanceReportImportResponse;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import java.io.IOException;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Admin: the college's attendance totals from a CSV file. Contract: docs/09-api-contract.md 8.16.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/imports")
public class AdminAttendanceReportController {

  private final AttendanceReportImportService imports;

  public AdminAttendanceReportController(AttendanceReportImportService imports) {
    this.imports = imports;
  }

  /** One part, "file": the CSV. The period is given as fromDate and toDate (yyyy-MM-dd). */
  @PostMapping(path = "/attendance-reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public AttendanceReportImportResponse importReports(
      @RequestPart("file") MultipartFile file,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate fromDate,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate toDate)
      throws IOException {
    if (file.getSize() > AttendanceReportImportService.MAX_BYTES) {
      throw new ApiException(ErrorCode.UPLOAD_TOO_LARGE, "The file is larger than 1 MB.");
    }
    return imports.importReports(file.getBytes(), file.getOriginalFilename(), fromDate, toDate);
  }
}
