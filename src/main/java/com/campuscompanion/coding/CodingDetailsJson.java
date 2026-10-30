package com.campuscompanion.coding;

import com.campuscompanion.coding.platform.PlatformDetails;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns an account's details into the JSON kept in coding_accounts.details and back. A document
 * that cannot be read (for example after a change of shape) gives empty details, and the next good
 * read replaces it.
 */
final class CodingDetailsJson {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private CodingDetailsJson() {}

  static String write(PlatformDetails details) {
    return MAPPER.writeValueAsString(details == null ? PlatformDetails.EMPTY : details);
  }

  static PlatformDetails read(String json) {
    if (json == null || json.isBlank()) {
      return PlatformDetails.EMPTY;
    }
    try {
      return MAPPER.readValue(json, PlatformDetails.class);
    } catch (JacksonException unreadable) {
      return PlatformDetails.EMPTY;
    }
  }
}
