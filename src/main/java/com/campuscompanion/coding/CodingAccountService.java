package com.campuscompanion.coding;

import com.campuscompanion.coding.dto.CodingAccountRequest;
import com.campuscompanion.coding.dto.CodingAccountResponse;
import com.campuscompanion.coding.dto.LeaderboardVisibility;
import com.campuscompanion.coding.dto.PortfolioResponse;
import com.campuscompanion.coding.platform.Handles;
import com.campuscompanion.coding.platform.PlatformException;
import com.campuscompanion.coding.platform.PlatformProfile;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ConflictException;
import com.campuscompanion.common.error.ErrorCode;
import com.campuscompanion.common.error.ResourceNotFoundException;
import com.campuscompanion.common.error.ValidationException;
import com.campuscompanion.common.security.CurrentUser;
import com.campuscompanion.student.StudentProfileService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A student's coding accounts (FR-COD-001, FR-COD-002): link, verify, refresh, remove, and the
 * leaderboard switch. The student is always the one of the session, so another student's account is
 * "not found". Verify and Refresh read an outside site, so they run without a transaction around
 * the call and open short ones before and after it.
 */
@Service
@PreAuthorize("hasRole('STUDENT')")
public class CodingAccountService {

  /** The least time between two Verify presses on a pending account. */
  static final Duration VERIFY_COOLDOWN = Duration.ofSeconds(20);

  private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int CODE_LENGTH = 8;

  private final CodingAccountRepository accounts;
  private final LeaderboardOptinRepository optins;
  private final CodingSync sync;
  private final CodingPortfolios portfolios;
  private final StudentProfileService students;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public CodingAccountService(
      CodingAccountRepository accounts,
      LeaderboardOptinRepository optins,
      CodingSync sync,
      CodingPortfolios portfolios,
      StudentProfileService students,
      TransactionTemplate transactions,
      Clock clock) {
    this.accounts = accounts;
    this.optins = optins;
    this.sync = sync;
    this.portfolios = portfolios;
    this.students = students;
    this.transactions = transactions;
    this.clock = clock;
  }

  public PortfolioResponse portfolio(CurrentUser user) {
    Long studentId = students.currentStudent(user).studentId();
    return transactions.execute(status -> portfolios.of(studentId, true));
  }

  /** Links an account as pending, with a new verification code. */
  public CodingAccountResponse connect(CurrentUser user, CodingAccountRequest request) {
    Long studentId = students.currentStudent(user).studentId();
    String handle =
        Handles.normalise(request.platform(), request.handle())
            .orElseThrow(
                () ->
                    new ValidationException(
                        "handle", "Enter the username as it appears on your profile."));
    return transactions.execute(
        status -> {
          if (accounts.existsByStudentIdAndPlatform(studentId, request.platform())) {
            throw new ConflictException(
                "You have already linked a "
                    + request.platform().displayName()
                    + " account. Remove it first.");
          }
          if (accounts.verifiedElsewhere(request.platform(), handle, -1L)) {
            throw new ConflictException("This account is already verified by another student.");
          }
          CodingAccount account =
              accounts.save(new CodingAccount(studentId, request.platform(), handle, newCode()));
          return CodingPortfolios.toResponse(account, true);
        });
  }

  /** Looks for the code in the profile; when found, the account is verified and read. */
  public CodingAccountResponse verify(CurrentUser user, Long accountId) {
    Long studentId = students.currentStudent(user).studentId();
    CodingAccount account = startAttempt(studentId, accountId, VERIFY_COOLDOWN, false);
    if (account.isVerified()) {
      return CodingPortfolios.toResponse(account, true);
    }
    PlatformProfile profile = readOrThrow(account);
    if (!profile.mentions(account.getVerificationCode())) {
      throw new ApiException(
          ErrorCode.CODING_VERIFICATION_FAILED,
          "The code "
              + account.getVerificationCode()
              + " is not in your "
              + account.getPlatform().displayName()
              + " profile yet. Save it there, then try again.");
    }
    try {
      return transactions.execute(
          status -> {
            CodingAccount current = owned(studentId, accountId);
            if (accounts.verifiedElsewhere(current.getPlatform(), current.getHandle(), accountId)) {
              throw new ConflictException("This account is already verified by another student.");
            }
            current.verify(clock.instant());
            sync.apply(current, profile.stats());
            return CodingPortfolios.toResponse(current, true);
          });
    } catch (DataIntegrityViolationException raced) {
      throw new ConflictException("This account is already verified by another student.");
    }
  }

  /** Reads a verified account again, at most once every 15 minutes. */
  public CodingAccountResponse refresh(CurrentUser user, Long accountId) {
    Long studentId = students.currentStudent(user).studentId();
    CodingAccount account =
        startAttempt(studentId, accountId, CodingPortfolios.REFRESH_COOLDOWN, true);
    PlatformProfile profile;
    try {
      profile = sync.read(account);
    } catch (PlatformException failure) {
      sync.recordFailure(accountId, failure);
      throw toApiException(failure, account);
    }
    return transactions.execute(
        status -> {
          CodingAccount current = owned(studentId, accountId);
          sync.apply(current, profile.stats());
          return CodingPortfolios.toResponse(current, true);
        });
  }

  /** Removes the account and its history. */
  public void remove(CurrentUser user, Long accountId) {
    Long studentId = students.currentStudent(user).studentId();
    transactions.executeWithoutResult(status -> accounts.delete(owned(studentId, accountId)));
  }

  /** Switches the student on or off the students' leaderboard. */
  public LeaderboardVisibility setVisibility(CurrentUser user, LeaderboardVisibility request) {
    Long studentId = students.currentStudent(user).studentId();
    transactions.executeWithoutResult(
        status -> {
          boolean shown = optins.existsById(studentId);
          if (request.show() && !shown) {
            optins.save(new LeaderboardOptin(studentId));
          } else if (!request.show() && shown) {
            optins.deleteById(studentId);
          }
        });
    return new LeaderboardVisibility(request.show());
  }

  /** Checks the waiting time and notes the attempt, in its own transaction. */
  private CodingAccount startAttempt(
      Long studentId, Long accountId, Duration cooldown, boolean mustBeVerified) {
    return transactions.execute(
        status -> {
          CodingAccount account = owned(studentId, accountId);
          if (mustBeVerified && !account.isVerified()) {
            throw new ConflictException("Verify this account first.");
          }
          if (!mustBeVerified && account.isVerified()) {
            return account;
          }
          Instant now = clock.instant();
          Duration left = CodingRules.waitLeft(account.getLastAttemptAt(), cooldown, now);
          if (!left.isZero()) {
            throw new ApiException(ErrorCode.RATE_LIMITED, waitMessage(left));
          }
          account.attempted(now);
          return accounts.save(account);
        });
  }

  private PlatformProfile readOrThrow(CodingAccount account) {
    try {
      return sync.read(account);
    } catch (PlatformException failure) {
      throw toApiException(failure, account);
    }
  }

  private CodingAccount owned(Long studentId, Long accountId) {
    return accounts
        .findByIdAndStudentId(accountId, studentId)
        .orElseThrow(() -> new ResourceNotFoundException());
  }

  private static ApiException toApiException(PlatformException failure, CodingAccount account) {
    String site = account.getPlatform().displayName();
    return failure.kind() == PlatformException.Kind.NOT_FOUND
        ? new ApiException(
            ErrorCode.CODING_PROFILE_NOT_FOUND,
            "No " + site + " profile is called " + account.getHandle() + ".")
        : new ApiException(
            ErrorCode.CODING_PLATFORM_UNAVAILABLE,
            site + " did not answer. Try again in a few minutes.");
  }

  private static String waitMessage(Duration left) {
    long seconds = Math.max(1, left.toSeconds() + (left.toMillisPart() > 0 ? 1 : 0));
    return seconds < 60
        ? "Wait " + seconds + " seconds before trying again."
        : "Wait " + ((seconds + 59) / 60) + " minutes before trying again.";
  }

  private String newCode() {
    StringBuilder code = new StringBuilder("CC-");
    for (int i = 0; i < CODE_LENGTH; i++) {
      code.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
    }
    return code.toString();
  }
}
