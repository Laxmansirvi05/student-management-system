package com.campuscompanion.ai;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;

/** The AI provider answered, but not in the structure and size the application accepts. */
public class AiInvalidResponseException extends ApiException {

  public AiInvalidResponseException(String message) {
    super(ErrorCode.AI_INVALID_RESPONSE, message);
  }
}
