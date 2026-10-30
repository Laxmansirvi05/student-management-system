package com.campuscompanion.coding;

import com.campuscompanion.coding.platform.PlatformException;
import com.campuscompanion.coding.platform.PlatformProfile;
import com.campuscompanion.coding.platform.PlatformReaders;
import com.campuscompanion.coding.platform.PlatformStats;
import com.campuscompanion.common.config.CampusTime;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reads an account's public profile and stores what it gives. The outside call is made with no
 * transaction open, so a slow site never holds a database connection; each store is its own short
 * transaction.
 */
@Component
public class CodingSync {

  private final CodingAccountRepository accounts;
  private final CodingSnapshotRepository snapshots;
  private final PlatformReaders readers;
  private final BadgePictures pictures;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public CodingSync(
      CodingAccountRepository accounts,
      CodingSnapshotRepository snapshots,
      PlatformReaders readers,
      BadgePictures pictures,
      TransactionTemplate transactions,
      Clock clock) {
    this.accounts = accounts;
    this.snapshots = snapshots;
    this.readers = readers;
    this.pictures = pictures;
    this.transactions = transactions;
    this.clock = clock;
  }

  /** One read of the account's public profile. Must be called outside a transaction. */
  public PlatformProfile read(CodingAccount account) throws PlatformException {
    PlatformProfile profile = readers.of(account.getPlatform()).read(account.getHandle());
    return profile.withDetails(pictures.attach(profile.details()));
  }

  /** Stores a good read on the account, inside the caller's transaction, with today's snapshot. */
  public void apply(CodingAccount account, PlatformProfile profile) {
    Instant now = clock.instant();
    PlatformStats stats = profile.stats();
    account.apply(stats, CodingDetailsJson.write(profile.details()), now);
    accounts.saveAndFlush(account);
    snapshots.record(
        account.getId(), CampusTime.today(clock), stats.problemsSolved(), stats.contestRating());
  }

  /** Notes a failed read on the account; its last good numbers stay. */
  public void recordFailure(Long accountId, PlatformException failure) {
    transactions.executeWithoutResult(
        status ->
            accounts
                .findById(accountId)
                .ifPresent(account -> account.failed(failure.kind().name())));
  }

  /**
   * Reads one verified account and stores the result, for the nightly job. Returns false when the
   * read failed or the account is gone or no longer verified.
   */
  public boolean refresh(Long accountId) {
    CodingAccount account = accounts.findById(accountId).orElse(null);
    if (account == null || !account.isVerified()) {
      return false;
    }
    PlatformProfile profile;
    try {
      profile = read(account);
    } catch (PlatformException failure) {
      recordFailure(accountId, failure);
      return false;
    }
    return Boolean.TRUE.equals(
        transactions.execute(
            status ->
                accounts
                    .findById(accountId)
                    .filter(CodingAccount::isVerified)
                    .map(
                        current -> {
                          apply(current, profile);
                          return true;
                        })
                    .orElse(false)));
  }
}
