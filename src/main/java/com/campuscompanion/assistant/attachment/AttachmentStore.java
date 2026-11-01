package com.campuscompanion.assistant.attachment;

import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ResourceNotFoundException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Holds what was read from the files a student attached, for a short time, so a question can use a
 * file without the browser sending it again. It lives in the memory of the server only: nothing is
 * written to the database or to file storage, and a file that is not used for {@link #KEEP_FOR}, or
 * that no longer fits, is forgotten. A file belongs to the student who attached it: another student
 * asking for the same identifier gets "not found", as for any other record.
 *
 * <p>The limits keep a small host safe: a few files per student, and a ceiling on the memory of all
 * of them together (the oldest are forgotten first).
 */
@Component
public class AttachmentStore {

  public static final int PER_STUDENT = 6;
  public static final Duration KEEP_FOR = Duration.ofHours(2);
  static final long TOTAL_BYTES = 48L * 1024 * 1024;

  private static final SecureRandom RANDOM = new SecureRandom();

  private record Held(long studentId, Instant lastUsed, ReadFile file) {}

  private final Clock clock;
  private final Map<String, Held> held = new LinkedHashMap<>();

  public AttachmentStore(Clock clock) {
    this.clock = clock;
  }

  /** Keeps a file for the student and returns the identifier to ask for it by. */
  public synchronized String add(long studentId, ReadFile file) {
    forgetOld();
    if (countOf(studentId) >= PER_STUDENT) {
      throw new ConflictException(
          "You can keep at most "
              + PER_STUDENT
              + " attached files. Remove one before attaching another.");
    }
    String id = newId();
    held.put(id, new Held(studentId, clock.instant(), file));
    shrinkToFit(id);
    return id;
  }

  /** The student's file, or "not found" when it is not theirs, never existed, or was forgotten. */
  public synchronized ReadFile get(long studentId, String id) {
    forgetOld();
    Held entry = id == null ? null : held.get(id);
    if (entry == null || entry.studentId() != studentId) {
      throw new ResourceNotFoundException();
    }
    held.put(id, new Held(studentId, clock.instant(), entry.file()));
    return entry.file();
  }

  /** Forgets a file. A file that is not the student's, or is already gone, is ignored. */
  public synchronized void remove(long studentId, String id) {
    Held entry = id == null ? null : held.get(id);
    if (entry != null && entry.studentId() == studentId) {
      held.remove(id);
    }
  }

  /** How many files the student has attached right now. */
  public synchronized int count(long studentId) {
    forgetOld();
    return countOf(studentId);
  }

  private int countOf(long studentId) {
    int count = 0;
    for (Held entry : held.values()) {
      if (entry.studentId() == studentId) {
        count++;
      }
    }
    return count;
  }

  private void forgetOld() {
    Instant limit = clock.instant().minus(KEEP_FOR);
    held.values().removeIf(entry -> entry.lastUsed().isBefore(limit));
  }

  /** Forgets the oldest files until all of them fit; the file just added is the last to go. */
  private void shrinkToFit(String newest) {
    long total = 0;
    for (Held entry : held.values()) {
      total += entry.file().weight();
    }
    List<String> oldestFirst = new ArrayList<>(held.keySet());
    Iterator<String> next = oldestFirst.iterator();
    while (total > TOTAL_BYTES && next.hasNext()) {
      String id = next.next();
      if (id.equals(newest)) {
        continue;
      }
      total -= held.remove(id).file().weight();
    }
  }

  private static String newId() {
    byte[] bytes = new byte[16];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
