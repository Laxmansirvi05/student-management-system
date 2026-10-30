package com.campuscompanion.coding.platform;

/** Reads one coding site's public profile of a handle. */
public interface PlatformReader {

  CodingPlatform platform();

  /**
   * Reads the profile.
   *
   * @param handle a handle that has passed {@link Handles#normalise}
   * @throws PlatformException NOT_FOUND when there is no such user, UNAVAILABLE otherwise
   */
  PlatformProfile read(String handle) throws PlatformException;
}
