package app.ari.api.feature.outbox.domain.repository;

import app.ari.api.feature.outbox.domain.model.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Stores events until the publisher has sent them. */
public interface OutboxRepository {

  void insert(OutboxEvent event);

  /**
   * Locks up to {@code limit} unsent events, oldest first, until the transaction ends. Rows another
   * publisher holds are skipped, so two instances never send the same event at once.
   */
  List<OutboxEvent> lockUnsent(int limit);

  void markSent(List<UUID> ids, Instant at);

  int deleteSentBefore(Instant before);

  /** Whether the consumer has a record of handling the event. */
  boolean isProcessed(String consumer, String eventId);

  /** Records it; false when the record was there already. */
  boolean insertProcessed(String consumer, String eventId, Instant at);

  int deleteProcessedBefore(Instant before);
}
