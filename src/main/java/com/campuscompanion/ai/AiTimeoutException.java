package com.campuscompanion.ai;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;

/** The AI provider did not answer within the time budget. */
public class AiTimeoutException extends ApiException {

  public AiTimeoutException(String message) {
    super(ErrorCode.AI_TIMEOUT, message);
  }
}
