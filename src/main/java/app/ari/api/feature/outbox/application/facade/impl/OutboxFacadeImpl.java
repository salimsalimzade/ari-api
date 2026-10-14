package app.ari.api.feature.outbox.application.facade.impl;

import app.ari.api.feature.outbox.application.facade.OutboxFacade;
import app.ari.api.feature.outbox.application.model.request.NewOutboxEvent;
import app.ari.api.feature.outbox.domain.service.OutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxFacadeImpl implements OutboxFacade {

  private final OutboxService outboxService;

  public OutboxFacadeImpl(OutboxService outboxService) {
    this.outboxService = outboxService;
  }

  /** MANDATORY: an event written outside the business change's transaction could be lost. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void add(NewOutboxEvent event) {
    outboxService.add(
        event.aggregateType(),
        event.aggregateId(),
        event.eventType(),
        event.routingKey(),
        event.payload());
  }

  @Override
  @Transactional
  public int publishPending() {
    return outboxService.publishPending();
  }

  @Override
  @Transactional
  public int deleteOldSent() {
    return outboxService.deleteOldSent();
  }

  @Override
  public boolean wasProcessed(String consumer, String eventId) {
    return outboxService.wasProcessed(consumer, eventId);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean markProcessed(String consumer, String eventId) {
    return outboxService.markProcessed(consumer, eventId);
  }

  @Override
  @Transactional
  public int deleteOldProcessed() {
    return outboxService.deleteOldProcessed();
  }
}
