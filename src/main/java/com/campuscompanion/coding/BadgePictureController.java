package com.campuscompanion.coding;

import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The kept copy of a badge picture (ADR 0017, addendum 2), for students and admins. A copy never
 * changes, so it may be cached. Opened on its own, the picture can run nothing: the answer carries
 * a policy that allows no script and no outside resource.
 */
@RestController
public class BadgePictureController {

  private final BadgePictureService pictures;

  public BadgePictureController(BadgePictureService pictures) {
    this.pictures = pictures;
  }

  @GetMapping("/api/v1/coding-badges/{key}")
  public ResponseEntity<byte[]> picture(@PathVariable String key) {
    CodingBadgeImage picture = pictures.picture(key);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(picture.getContentType()))
        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable())
        .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
        .header("X-Content-Type-Options", "nosniff")
        .body(picture.getData());
  }
}
