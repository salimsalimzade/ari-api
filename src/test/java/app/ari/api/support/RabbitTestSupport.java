package app.ari.api.support;

import app.ari.api.config.RabbitConfig;
import java.time.Duration;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/** RabbitMQ helpers for tests that need their own queues. */
public final class RabbitTestSupport {

  private RabbitTestSupport() {}

  /** Declares a work queue with its delay and dead-letter queues, as RabbitConfig does. */
  public static void declareWorkQueue(AmqpAdmin admin, DirectExchange exchange, String name) {
    for (Declarable declarable : RabbitConfig.queueWithRetries(name, exchange)) {
      if (declarable instanceof Queue queue) {
        admin.declareQueue(queue);
      } else if (declarable instanceof Binding binding) {
        admin.declareBinding(binding);
      }
    }
  }

  /** Takes messages off the queue until the one with this id arrives. */
  public static Message receive(
      RabbitTemplate rabbit, String queue, String messageId, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      Message message = rabbit.receive(queue, 500);
      if (message != null && messageId.equals(message.getMessageProperties().getMessageId())) {
        return message;
      }
    }
    throw new AssertionError("No message " + messageId + " on " + queue + " within " + timeout);
  }
}
