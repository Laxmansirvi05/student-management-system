package com.campuscompanion.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One AI request, as numbers and labels only: who, which tool, how long the texts were, how it
 * ended and how long it took. It has no field for the text a student sent or the text the AI
 * returned, so neither can be stored by mistake. The class has no setters: a row is never changed.
 */
@Entity
@Table(name = "ai_request_logs")
public class AiRequestLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, updatable = false)
  private Long userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30, updatable = false)
  private AiFeature feature;

  @Column(name = "prompt_version", nullable = false, length = 30, updatable = false)
  private String promptVersion;

  @Column(name = "input_chars", nullable = false, updatable = false)
  private int inputChars;

  @Column(name = "output_chars", updatable = false)
  private Integer outputChars;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 15, updatable = false)
  private AiRequestStatus status;

  @Column(name = "duration_ms", updatable = false)
  private Integer durationMs;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** Required by JPA. */
  protected AiRequestLog() {}

  public AiRequestLog(
      Long userId,
      AiFeature feature,
      String promptVersion,
      int inputChars,
      Integer outputChars,
      AiRequestStatus status,
      long durationMs,
      Instant createdAt) {
    this.userId = userId;
    this.feature = feature;
    this.promptVersion = promptVersion;
    this.inputChars = inputChars;
    this.outputChars = outputChars;
    this.status = status;
    this.durationMs = (int) Math.min(durationMs, Integer.MAX_VALUE);
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public AiRequestStatus getStatus() {
    return status;
  }
}
