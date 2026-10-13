package app.ari.api.common.client;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * What a consumer does with a message it couldn't handle: move it to the next delay queue, or to
 * the dead-letter queue after the last delay or when it can never succeed. The consumer then
 * returns normally, so RabbitMQ drops the original. The move waits for RabbitMQ's confirm; if it
 * fails, the exception makes RabbitMQ deliver the original again.
 */
@Component
public class FailedMessageHandler {

  /** How many retries the message already had. */
  public static final String ATTEMPT_HEADER = "x-ari-attempt";

  /** Why a dead-lettered message failed the last time. */
  public static final String ERROR_HEADER = "x-ari-error";

  private static final Logger log = LoggerFactory.getLogger(FailedMessageHandler.class);
  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

  private final RabbitTemplate rabbit;

  public FailedMessageHandler(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  public void handle(Message message, String queue, RuntimeException failure) {
    MessageProperties properties = message.getMessageProperties();
    int retries = properties.getHeader(ATTEMPT_HEADER) instanceof Number n ? n.intValue() : 0;
    boolean retryable = !(failure instanceof UnprocessableMessageException);
    String target;
    if (retryable && retries < Queues.RETRY_DELAYS.size()) {
      Duration delay = Queues.RETRY_DELAYS.get(retries);
      target = Queues.retry(queue, delay);
      properties.setHeader(ATTEMPT_HEADER, retries + 1);
      log.warn(
          "{}: message {} failed, retrying in {}s: {}",
          queue,
          properties.getMessageId(),
          delay.toSeconds(),
          failure.getMessage());
    } else {
      target = Queues.deadLetter(queue);
      properties.setHeader(ERROR_HEADER, String.valueOf(failure.getMessage()));
      log.error(
          "{}: message {} failed after {} retries, moved to {}",
          queue,
          properties.getMessageId(),
          retries,
          target,
          failure);
    }
    // A received message carries no delivery mode of its own, so set it again.
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    rabbit.invoke(
        operations -> {
          operations.send("", target, message);
          operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
          return target;
        });
  }
}
