package app.ari.api.common.client;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Sends messages to RabbitMQ and waits until the broker has stored them (publisher confirms), so a
 * caller only treats a message as sent once RabbitMQ has it.
 */
@Component
public class MessagePublisher {

  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

  private final RabbitTemplate rabbit;

  public MessagePublisher(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  /** Sends them on one channel; throws when RabbitMQ refuses one or doesn't confirm in time. */
  public void publishAll(List<OutgoingMessage> messages) {
    rabbit.invoke(
        operations -> {
          for (OutgoingMessage message : messages) {
            operations.send(Queues.EXCHANGE, message.routingKey(), toAmqp(message));
          }
          operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
          return messages.size();
        });
  }

  private static Message toAmqp(OutgoingMessage message) {
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(message.id());
    properties.setType(message.type());
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setContentEncoding(StandardCharsets.UTF_8.name());
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    return new Message(message.json().getBytes(StandardCharsets.UTF_8), properties);
  }
}
