package com.campuscompanion.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.campuscompanion.auth.Role;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.storage.StorageUnavailableException;
import com.campuscompanion.resource.dto.ResourceMetadata;
import com.campuscompanion.subject.SubjectService;
import com.campuscompanion.subject.dto.SubjectResponse;
import com.campuscompanion.support.TestPdfs;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/** What an upload does when the database or the storage fails half-way (scenarios UP7, UP8). */
class AdminResourceServiceTest {

  private static final CurrentUser ADMIN = new CurrentUser(1L, Role.ADMIN);
  private static final ResourceMetadata NOTES =
      new ResourceMetadata("Unit 1 notes", null, 7L, null, ResourceType.NOTES, null);

  private final LearningResourceRepository resources = mock(LearningResourceRepository.class);
  private final QuestionPaperRepository papers = mock(QuestionPaperRepository.class);
  private final StorageClient storage = mock(StorageClient.class);
  private final SubjectService subjects = mock(SubjectService.class);
  private final ResourceAssembler assembler = mock(ResourceAssembler.class);

  private AdminResourceService service;

  @BeforeEach
  void setUp() {
    service =
        new AdminResourceService(
            resources, papers, assembler, storage, new PdfValidator(), subjects);
    SubjectResponse subject = mock(SubjectResponse.class);
    when(subject.code()).thenReturn("CS301");
    when(subjects.find(7L)).thenReturn(Optional.of(subject));
  }

  @Test
  void aFileIsCheckedBeforeAnythingIsStored() {
    assertThatThrownBy(
            () ->
                service.upload(NOTES, TestPdfs.plainText(), "application/pdf", "notes.pdf", ADMIN))
        .isInstanceOf(com.campuscompanion.common.error.ApiException.class);

    verify(storage, never()).upload(anyString(), any(), anyString());
    verify(resources, never()).saveAndFlush(any());
  }

  @Test
  void whenStorageFailsNoRowIsSaved() {
    org.mockito.Mockito.doThrow(new StorageUnavailableException())
        .when(storage)
        .upload(anyString(), any(), anyString());

    assertThatThrownBy(
            () -> service.upload(NOTES, TestPdfs.valid(1), "application/pdf", "notes.pdf", ADMIN))
        .isInstanceOf(StorageUnavailableException.class);

    verify(resources, never()).saveAndFlush(any());
  }

  @Test
  void whenTheDatabaseFailsAfterTheUploadTheStoredFileIsRemoved() {
    when(resources.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("the database refused the row"));

    assertThatThrownBy(
            () -> service.upload(NOTES, TestPdfs.valid(1), "application/pdf", "notes.pdf", ADMIN))
        .isInstanceOf(DataIntegrityViolationException.class);

    ArgumentCaptor<String> storedAt = ArgumentCaptor.forClass(String.class);
    InOrder order = inOrder(storage);
    order.verify(storage).upload(storedAt.capture(), any(), eq("application/pdf"));
    order.verify(storage).delete(storedAt.getValue());
  }

  @Test
  void theStoragePathIsBuiltByTheServerAndNeverFromTheFileName() {
    when(resources.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("stop after the upload"));

    assertThatThrownBy(
        () ->
            service.upload(
                NOTES, TestPdfs.valid(1), "application/pdf", "../../etc/passwd.pdf", ADMIN));

    ArgumentCaptor<String> storedAt = ArgumentCaptor.forClass(String.class);
    verify(storage).upload(storedAt.capture(), any(), anyString());
    assertThat(storedAt.getValue())
        .matches(
            "resources/CS301/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");
  }

  @Test
  void theNameShownToUsersHasNoFoldersAndNoControlCharacters() {
    assertThat(AdminResourceService.displayName("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
    assertThat(AdminResourceService.displayName("C:\\Users\\demo\\notes.pdf"))
        .isEqualTo("notes.pdf");
    assertThat(AdminResourceService.displayName("unit\u0000 1\n.pdf")).isEqualTo("unit 1.pdf");
    assertThat(AdminResourceService.displayName(null)).isEqualTo("document.pdf");
    assertThat(AdminResourceService.displayName("a".repeat(300) + ".pdf"))
        .hasSize(255)
        .endsWith(".pdf");
  }
}
