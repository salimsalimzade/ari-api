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
}
