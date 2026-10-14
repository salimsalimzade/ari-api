package app.ari.api.feature.outbox.application.facade;

import app.ari.api.feature.outbox.application.model.request.NewOutboxEvent;

/** Background jobs for other features. Other features use only this facade. */
public interface OutboxFacade {

  /**
   * Adds the event in the caller's transaction, so it is sent only if that transaction commits.
   * Throws {@code IllegalTransactionStateException} when called without one.
   */
  void add(NewOutboxEvent event);

  /**
   * Sends the oldest unsent events to RabbitMQ and marks them sent, all or nothing. The scheduler
   * calls it every second; returns how many were sent.
   */
  int publishPending();

  /** Deletes events sent more than a week ago; returns how many. */
  int deleteOldSent();

  /** Whether the consumer has handled this event already, so a second delivery can be dropped. */
  boolean wasProcessed(String consumer, String eventId);

  /**
   * Records that the consumer handled the event, in the caller's transaction, so it counts only if
   * the consumer's change commits. Returns false when it was recorded already. Throws {@code
   * IllegalTransactionStateException} when called without a transaction.
   */
  boolean markProcessed(String consumer, String eventId);

  /** Deletes the records of events handled more than 30 days ago; returns how many. */
  int deleteOldProcessed();
}
