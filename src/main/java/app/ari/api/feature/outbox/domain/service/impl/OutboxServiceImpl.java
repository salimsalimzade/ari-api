package app.ari.api.feature.outbox.domain.service.impl;

import app.ari.api.common.client.MessagePublisher;
import app.ari.api.common.client.OutgoingMessage;
import app.ari.api.feature.outbox.domain.model.OutboxEvent;
import app.ari.api.feature.outbox.domain.repository.OutboxRepository;
import app.ari.api.feature.outbox.domain.service.OutboxService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OutboxServiceImpl implements OutboxService {

  private static final int BATCH_SIZE = 100;
  private static final Duration KEEP_SENT = Duration.ofDays(7);

  private final OutboxRepository events;
  private final MessagePublisher publisher;
  private final JsonMapper jsonMapper;
  private final Clock clock;

  public OutboxServiceImpl(
      OutboxRepository events, MessagePublisher publisher, JsonMapper jsonMapper, Clock clock) {
    this.events = events;
    this.publisher = publisher;
    this.jsonMapper = jsonMapper;
    this.clock = clock;
  }

  @Override
  public void add(
      String aggregateType, UUID aggregateId, String eventType, String routingKey, Object payload) {
    events.insert(
        new OutboxEvent(
            UUID.randomUUID(),
            aggregateType,
            aggregateId,
            eventType,
            routingKey,
            jsonMapper.writeValueAsString(payload),
            clock.instant(),
            null));
  }

  /**
   * Marks the events sent only after RabbitMQ confirmed them. A crash in between sends them again
   * later, which consumers ignore by event id.
   */
  @Override
  public int publishPending() {
    List<OutboxEvent> pending = events.lockUnsent(BATCH_SIZE);
    if (pending.isEmpty()) {
      return 0;
    }
    publisher.publishAll(
        pending.stream()
            .map(
                event ->
                    new OutgoingMessage(
                        event.id().toString(),
                        event.eventType(),
                        event.routingKey(),
                        event.payload()))
            .toList());
    events.markSent(pending.stream().map(OutboxEvent::id).toList(), clock.instant());
    return pending.size();
  }

  @Override
  public int deleteOldSent() {
    return events.deleteSentBefore(clock.instant().minus(KEEP_SENT));
  }
}
