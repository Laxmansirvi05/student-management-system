package com.campuscompanion.pyq;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.pyq.dto.PyqLinkResponse;
import com.campuscompanion.pyq.dto.PyqMetadata;
import com.campuscompanion.pyq.dto.PyqPaperResponse;
import com.campuscompanion.pyq.dto.PyqUpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Admin: managing previous-year papers. Contract: docs/09-api-contract.md section 8.18. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/pyqs")
public class AdminPyqController {

  private final AdminPyqService pyqs;

  public AdminPyqController(AdminPyqService pyqs) {
    this.pyqs = pyqs;
  }

  @GetMapping
  public PageResponse<PyqPaperResponse> list(
      @RequestParam(required = false) String branch,
      @RequestParam(required = false) PyqStatus status,
      @RequestParam(required = false) String q,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return pyqs.list(branch, status, q, page, size);
  }

  /**
   * The part "file" is the PDF. The details come either as a JSON part "metadata" or as "path",
   * where the file sits in the organised folder, such as {@code CSE/Machine
   * Learning/2025(December)-supply.pdf}.
   */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public PyqPaperResponse upload(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "metadata", required = false) @Valid PyqMetadata metadata,
      @RequestParam(value = "path", required = false) String path,
      @AuthenticationPrincipal CurrentUser admin)
      throws IOException {
    PyqMetadata details = metadata != null ? metadata : PyqPathParser.parse(path);
    return pyqs.upload(
        details, file.getBytes(), file.getContentType(), file.getOriginalFilename(), admin);
  }

  @GetMapping("/{id}/link")
  public PyqLinkResponse link(@PathVariable Long id) {
    return pyqs.link(id);
  }

  @PutMapping("/{id}")
  public PyqPaperResponse update(
      @PathVariable Long id, @Valid @RequestBody PyqUpdateRequest request) {
    return pyqs.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    pyqs.delete(id);
    return ResponseEntity.noContent().build();
  }
}
