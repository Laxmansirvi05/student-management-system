package com.campuscompanion.coding;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** This application's copy of one badge picture from a coding site; never changed once kept. */
@Entity
@Table(name = "coding_badge_images")
public class CodingBadgeImage {

  @Id private String id;

  @Column(nullable = false, updatable = false, length = 300)
  private String source;

  @Column(name = "content_type", nullable = false, updatable = false, length = 20)
  private String contentType;

  @Column(nullable = false, updatable = false)
  private byte[] data;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  /** Required by JPA. */
  protected CodingBadgeImage() {}

  public CodingBadgeImage(String id, String source, String contentType, byte[] data) {
    this.id = id;
    this.source = source;
    this.contentType = contentType;
    this.data = data;
  }

  public String getId() {
    return id;
  }

  public String getContentType() {
    return contentType;
  }

  public byte[] getData() {
    return data;
  }
}
