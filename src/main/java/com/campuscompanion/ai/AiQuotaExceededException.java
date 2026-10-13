package com.campuscompanion.ai;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;

/** The student has used all of today's AI requests. */
public class AiQuotaExceededException extends ApiException {

  public AiQuotaExceededException(String message) {
    super(ErrorCode.AI_QUOTA_EXCEEDED, message);
  }
}
