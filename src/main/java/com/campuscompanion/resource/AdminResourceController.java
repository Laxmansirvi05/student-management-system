package com.campuscompanion.resource;

import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.web.PageResponse;
import com.campuscompanion.resource.dto.ResourceMetadata;
import com.campuscompanion.resource.dto.ResourceResponse;
import com.campuscompanion.resource.dto.ResourceUpdateRequest;
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

/** Admin: uploading and managing resources. Contract: docs/09-api-contract.md section 8.11. */
@RestController
@Validated
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/v1/admin/resources")
public class AdminResourceController {

  private final AdminResourceService resources;

  public AdminResourceController(AdminResourceService resources) {
    this.resources = resources;
  }

  @GetMapping
  public PageResponse<ResourceResponse> list(
      @RequestParam(required = false) Long subjectId,
      @RequestParam(required = false) ResourceStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return resources.list(subjectId, status, page, size);
  }

  /** Two parts: "metadata" as JSON and "file" as the PDF. */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public ResourceResponse upload(
      @RequestPart("metadata") @Valid ResourceMetadata metadata,
      @RequestPart("file") MultipartFile file,
      @AuthenticationPrincipal CurrentUser admin)
      throws IOException {
    return resources.upload(
        metadata, file.getBytes(), file.getContentType(), file.getOriginalFilename(), admin);
  }

  @PutMapping("/{id}")
  public ResourceResponse update(
      @PathVariable Long id, @Valid @RequestBody ResourceUpdateRequest request) {
    return resources.update(id, request);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    resources.delete(id);
    return ResponseEntity.noContent().build();
  }
}
