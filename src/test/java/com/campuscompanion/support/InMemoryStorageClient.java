package com.campuscompanion.support;

import com.campuscompanion.common.storage.StorageClient;
import com.campuscompanion.common.storage.StorageUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * File storage held in memory, used by every integration test in place of Supabase Storage. Tests
 * never reach a real bucket. Each kind of call can be made to fail, to check what the application
 * does when storage is down.
 */
@Component
@Primary
public class InMemoryStorageClient implements StorageClient {

  private final Map<String, byte[]> files = new ConcurrentHashMap<>();
  private volatile boolean failUploads;
  private volatile boolean failDeletes;
  private volatile boolean failSigning;
  private volatile String lastWritten;

  @Override
  public void upload(String path, byte[] content, String contentType) {
    if (failUploads) {
      throw new StorageUnavailableException();
    }
    files.put(path, content.clone());
    lastWritten = path;
  }

  @Override
  public void delete(String path) {
    if (failDeletes) {
      throw new StorageUnavailableException();
    }
    files.remove(path);
  }

  @Override
  public SignedLink signedUrl(String path, Duration validFor, String downloadName) {
    if (failSigning || !files.containsKey(path)) {
      throw new StorageUnavailableException();
    }
    return new SignedLink(
        "https://storage.example/signed/"
            + path
            + "?token=test"
            + (downloadName == null ? "" : "&download=" + downloadName),
        Instant.now().plus(validFor));
  }

  /** The path of the most recent successful upload. */
  public String lastWritten() {
    return lastWritten;
  }

  public Set<String> paths() {
    return Set.copyOf(files.keySet());
  }

  public byte[] contentAt(String path) {
    return files.get(path);
  }

  public void failUploads(boolean fail) {
    this.failUploads = fail;
  }

  public void failDeletes(boolean fail) {
    this.failDeletes = fail;
  }

  public void failSigning(boolean fail) {
    this.failSigning = fail;
  }

  /** Back to working storage. Stored files are kept, because other tests may still use them. */
  public void workAgain() {
    failUploads = false;
    failDeletes = false;
    failSigning = false;
  }
}
