package app.ari.api.feature.outbox.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A background job waiting to be sent. {@code id} is also the event id consumers deduplicate by;
 * {@code payload} is JSON; {@code sentAt} is null until RabbitMQ confirmed it.
 */
public record OutboxEvent(
    UUID id,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    String routingKey,
    String payload,
    Instant createdAt,
    Instant sentAt) {}
