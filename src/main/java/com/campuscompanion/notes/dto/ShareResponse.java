package com.campuscompanion.notes.dto;

import java.util.List;

/** The sharing state of a note. {@code share} is null when it was never shared. */
public record ShareResponse(ShareState share) {

  public record ShareState(
      String slug, String linkAccess, List<ShareRecipientResponse> recipients) {}
}
