package com.campuscompanion.assistant.attachment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campuscompanion.ai.provider.AiImage;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** What is kept of an attached file, for whom, and for how long. */
class AttachmentStoreTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-11-02T09:00:00Z"));
  private final AttachmentStore store = new AttachmentStore(clock);

  private static ReadFile file(String name) {
    return new ReadFile(
        name, ReadFile.Kind.DOCX, 0, 0, List.of(Segment.text("hello")), List.of(), null);
  }

  private static ReadFile heavy(String name, int megabytes) {
    AiImage image = new AiImage("image/jpeg", new byte[megabytes * 1024 * 1024]);
    return new ReadFile(
        name, ReadFile.Kind.IMAGE, 0, 0, List.of(Segment.picture("", 0)), List.of(image), null);
  }

  @Test
  void aStudentGetsBackTheirOwnFile() {
    String id = store.add(1, file("a.docx"));
    assertThat(id).hasSize(32);
    assertThat(store.get(1, id).name()).isEqualTo("a.docx");
  }

  @Test
  void anotherStudentCannotUseTheFileOrTellThatItExists() {
    String id = store.add(1, file("a.docx"));
    assertThatThrownBy(() -> store.get(2, id)).isInstanceOf(ResourceNotFoundException.class);
    store.remove(2, id); // ignored: it is not theirs
    assertThat(store.get(1, id)).isNotNull();
  }

  @Test
  void aFileIsForgottenAfterTwoHoursWithoutUse() {
    String id = store.add(1, file("a.docx"));
    clock.advance(AttachmentStore.KEEP_FOR.plusMinutes(1));
    assertThatThrownBy(() -> store.get(1, id)).isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void usingAFileKeepsItLonger() {
    String id = store.add(1, file("a.docx"));
    clock.advance(Duration.ofMinutes(90));
    store.get(1, id);
    clock.advance(Duration.ofMinutes(90));
    assertThat(store.get(1, id)).isNotNull();
  }

  @Test
  void aStudentCanKeepOnlyAFewFilesAtATime() {
    for (int i = 0; i < AttachmentStore.PER_STUDENT; i++) {
      store.add(1, file("f" + i));
    }
    assertThatThrownBy(() -> store.add(1, file("one too many")))
        .isInstanceOf(ConflictException.class);
    store.add(2, file("another student is not affected"));
  }

  @Test
  void removingAFileMakesRoomAndForgetsIt() {
    String id = store.add(1, file("a.docx"));
    store.remove(1, id);
    assertThatThrownBy(() -> store.get(1, id)).isInstanceOf(ResourceNotFoundException.class);
    assertThat(store.count(1)).isZero();
  }

  @Test
  void whenMemoryIsShortTheOldestFilesAreForgottenFirst() {
    String first = store.add(1, heavy("first", 20));
    String second = store.add(2, heavy("second", 20));
    String third = store.add(3, heavy("third", 20));
    assertThatThrownBy(() -> store.get(1, first)).isInstanceOf(ResourceNotFoundException.class);
    assertThat(store.get(2, second)).isNotNull();
    assertThat(store.get(3, third)).isNotNull();
  }

  @Test
  void anIdThatWasNeverGivenIsNotFound() {
    assertThatThrownBy(() -> store.get(1, "nope")).isInstanceOf(ResourceNotFoundException.class);
    assertThatThrownBy(() -> store.get(1, null)).isInstanceOf(ResourceNotFoundException.class);
  }
}
