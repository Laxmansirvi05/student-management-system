package com.campuscompanion.ai;

import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;

/**
 * The assistant declined the request, for example because it is not a study topic. The message is
 * the reason it gave.
 */
public class AiRequestDeclinedException extends ApiException {

  public AiRequestDeclinedException(String message) {
    super(ErrorCode.AI_REQUEST_DECLINED, message);
  }
}
