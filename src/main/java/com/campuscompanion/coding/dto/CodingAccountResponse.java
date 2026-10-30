package com.campuscompanion.coding.dto;

import com.campuscompanion.coding.CodingAccountStatus;
import com.campuscompanion.coding.platform.CodingPlatform;
import java.time.Instant;

/**
 * One linked account and its last numbers. The verification code is only sent to its owner while
 * the account is pending; every number is null until the first good read.
 */
public record CodingAccountResponse(
    Long id,
    CodingPlatform platform,
    String platformName,
    String handle,
    String profileUrl,
    CodingAccountStatus status,
    String verificationCode,
    String codeHint,
    Instant verifiedAt,
    boolean countsProblems,
    Integer problemsSolved,
    Integer easySolved,
    Integer mediumSolved,
    Integer hardSolved,
    Integer contestRating,
    Integer maxRating,
    Integer contestsAttended,
    String rankLabel,
    Integer score,
    Integer publicRepos,
    Integer stars,
    Integer followers,
    Instant lastSyncedAt,
    String lastSyncError,
    Instant refreshAvailableAt) {}
