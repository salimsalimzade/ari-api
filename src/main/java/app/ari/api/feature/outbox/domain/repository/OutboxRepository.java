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
}
