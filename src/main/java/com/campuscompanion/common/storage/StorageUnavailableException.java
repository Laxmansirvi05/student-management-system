package com.campuscompanion.common.storage;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;

/** File storage could not be reached, or is not set up. The rest of the application still works. */
public class StorageUnavailableException extends ApiException {

  public StorageUnavailableException() {
    super(
        ErrorCode.STORAGE_UNAVAILABLE,
        "File storage is temporarily unavailable. Please try again in a little while.");
  }
}
