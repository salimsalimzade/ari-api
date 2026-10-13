package app.ari.api.common.client;

import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import app.ari.api.support.RabbitTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/** Retries through the delay queues of a real RabbitMQ, then the dead-letter queue. */
@IntegrationTest
class FailedMessageHandlerTest {

  /** A queue only these tests use. */
  private static final String QUEUE = "handler.test";

  @Autowired private FailedMessageHandler handler;
  @Autowired private RabbitTemplate rabbit;
  @Autowired private AmqpAdmin admin;
  @Autowired private DirectExchange ariExchange;

  @BeforeEach
  void declareTheTestQueue() {
    RabbitTestSupport.declareWorkQueue(admin, ariExchange, QUEUE);
  }

  @Test
  void aFailedMessageComesBackAfterItsFirstDelayWithItsRetryCounted() {
    String id = UUID.randomUUID().toString();

    handler.handle(message(id, null), QUEUE, new IllegalStateException("SMTP is down"));

    Message back = RabbitTestSupport.receive(rabbit, QUEUE, id, Duration.ofSeconds(10));
    MessageProperties properties = back.getMessageProperties();
    assertThat((Integer) properties.getHeader(FailedMessageHandler.ATTEMPT_HEADER)).isEqualTo(1);
    assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
  }

  @Test
  void afterTheLastRetryItGoesToTheDeadLetterQueue() {
    String id = UUID.randomUUID().toString();

    handler.handle(
        message(id, Queues.RETRY_DELAYS.size()), QUEUE, new IllegalStateException("still down"));

    Message dead =
        RabbitTestSupport.receive(rabbit, Queues.deadLetter(QUEUE), id, Duration.ofSeconds(5));
    assertThat((String) dead.getMessageProperties().getHeader(FailedMessageHandler.ERROR_HEADER))
        .isEqualTo("still down");
  }

  @Test
  void aMessageThatCanNeverSucceedSkipsTheRetries() {
    String id = UUID.randomUUID().toString();

    handler.handle(message(id, null), QUEUE, new UnprocessableMessageException("not JSON"));

    Message dead =
        RabbitTestSupport.receive(rabbit, Queues.deadLetter(QUEUE), id, Duration.ofSeconds(5));
    assertThat((String) dead.getMessageProperties().getHeader(FailedMessageHandler.ERROR_HEADER))
        .isEqualTo("not JSON");
  }

  /** A message as a consumer gets it, after {@code retries} earlier retries. */
  private static Message message(String id, Integer retries) {
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(id);
    if (retries != null) {
      properties.setHeader(FailedMessageHandler.ATTEMPT_HEADER, retries);
    }
    return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
  }
}
