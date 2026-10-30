package com.campuscompanion.coding;

import com.campuscompanion.common.error.ResourceNotFoundException;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Badge pictures for anyone logged in: they are public pictures from the sites, nothing personal.
 */
@Service
@PreAuthorize("isAuthenticated()")
public class BadgePictureService {

  private static final Pattern KEY = Pattern.compile("^[0-9a-f]{64}$");

  private final CodingBadgeImageRepository images;

  public BadgePictureService(CodingBadgeImageRepository images) {
    this.images = images;
  }

  /** The kept picture; a key that is not 64 lower-case hex characters is never looked up. */
  @Transactional(readOnly = true)
  public CodingBadgeImage picture(String key) {
    if (key == null || !KEY.matcher(key).matches()) {
      throw new ResourceNotFoundException();
    }
    return images.findById(key).orElseThrow(ResourceNotFoundException::new);
  }
}
