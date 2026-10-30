package com.campuscompanion.coding.platform;

/** A read of a coding site that did not give a profile. The site's own words are not kept. */
public class PlatformException extends Exception {

  /** Why the read failed. */
  public enum Kind {
    /** The site has no user with that handle. */
    NOT_FOUND,
    /** The site did not answer, refused, or answered in a way the reader does not understand. */
    UNAVAILABLE
  }

  private final Kind kind;

  public PlatformException(Kind kind) {
    super(kind.name());
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
