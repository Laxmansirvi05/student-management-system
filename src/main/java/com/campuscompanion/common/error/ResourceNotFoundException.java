package com.campuscompanion.common.error;

/**
 * The record does not exist, or it belongs to someone else. Both cases give the same answer so that
 * identifiers cannot be probed (SECURITY_RULES.md SR-15).
 */
public class ResourceNotFoundException extends ApiException {

  public ResourceNotFoundException() {
    super(ErrorCode.NOT_FOUND, "We could not find that.");
  }
}
