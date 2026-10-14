package app.ari.api.common.transaction;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Undoing work outside the database when the transaction rolls back, like deleting a file stored
 * for a row that was never committed.
 */
public final class OnRollback {

  private OnRollback() {}

  /** Runs if the current transaction rolls back; does nothing when there is no transaction. */
  public static void run(Runnable work) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_ROLLED_BACK) {
              work.run();
            }
          }
        });
  }
}
