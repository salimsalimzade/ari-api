package app.ari.api.feature.outbox.domain.service;

import java.util.UUID;

/** Writes background jobs. */
public interface OutboxService {

  /** Stores the event with a new id and the payload as JSON. */
  void add(
      String aggregateType, UUID aggregateId, String eventType, String routingKey, Object payload);

  /** Sends the oldest unsent events, at most 100, and marks them sent; returns how many. */
  int publishPending();

  /** Deletes events sent more than a week ago; returns how many. */
  int deleteOldSent();

  boolean wasProcessed(String consumer, String eventId);

  /** False when the consumer had the event recorded already. */
  boolean markProcessed(String consumer, String eventId);

  /** Deletes the records of events handled more than 30 days ago; returns how many. */
  int deleteOldProcessed();
}
