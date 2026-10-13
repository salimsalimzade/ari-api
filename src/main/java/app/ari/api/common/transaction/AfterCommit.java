package app.ari.api.common.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Work that must wait for the transaction to commit, like deleting a file whose row was just
 * deleted: a rollback must leave the file alone, or the row would point at nothing.
 */
public final class AfterCommit {

  private AfterCommit() {}

  /** Runs after the current transaction commits, or right away when there is none. */
  public static void run(Runnable work) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      work.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            work.run();
          }
        });
  }
}
