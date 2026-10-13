package app.ari.api.feature.outbox.application.model.request;

import java.util.UUID;

/**
 * A background job to send after the current transaction commits.
 *
 * @param aggregateType what the event is about, e.g. {@code USER}
 * @param aggregateId the id of that row
 * @param eventType e.g. {@code VerifyEmail}
 * @param routingKey the RabbitMQ queue it goes to, e.g. {@code email.transactional}
 * @param payload stored as JSON; the consumer reads it
 */
public record NewOutboxEvent(
    String aggregateType, UUID aggregateId, String eventType, String routingKey, Object payload) {}
