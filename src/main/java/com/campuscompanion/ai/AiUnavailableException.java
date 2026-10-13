package com.campuscompanion.ai;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;

/**
 * The assistant cannot answer right now: it is switched off, today's limit for everyone is reached,
 * or the AI provider failed.
 */
public class AiUnavailableException extends ApiException {

  public AiUnavailableException(String message) {
    super(ErrorCode.AI_UNAVAILABLE, message);
  }
}
