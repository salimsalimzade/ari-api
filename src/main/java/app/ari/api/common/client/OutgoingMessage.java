package app.ari.api.common.client;

/**
 * A message for RabbitMQ's {@code ari} exchange.
 *
 * @param id the event id consumers deduplicate by; becomes the AMQP message id
 * @param type the event type, e.g. {@code VerifyEmail}
 * @param routingKey the queue it goes to, e.g. {@code email.transactional}
 * @param json the body
 */
public record OutgoingMessage(String id, String type, String routingKey, String json) {}
