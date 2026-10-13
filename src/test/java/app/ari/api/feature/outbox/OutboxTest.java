package app.ari.api.feature.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import app.ari.api.common.client.Queues;
import app.ari.api.feature.outbox.application.facade.OutboxFacade;
import app.ari.api.feature.outbox.application.model.request.NewOutboxEvent;
import app.ari.api.support.IntegrationTest;
import app.ari.api.support.MutableClock;
import app.ari.api.support.RabbitTestSupport;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The outbox end to end: the scheduler sends committed events to a real RabbitMQ. */
@IntegrationTest
class OutboxTest {

  /** A queue only these tests use, so other consumers never take their messages. */
  private static final String TEST_QUEUE = "outbox.test";

  @Autowired private OutboxFacade outbox;
  @Autowired private TransactionTemplate transaction;
  @Autowired private RabbitTemplate rabbit;
  @Autowired private AmqpAdmin admin;
  @Autowired private DirectExchange ariExchange;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private MutableClock clock;

  @BeforeEach
  void declareTheTestQueue() {
    RabbitTestSupport.declareWorkQueue(admin, ariExchange, TEST_QUEUE);
  }

  @Test
  void aCommittedEventIsSentToItsQueueAndMarkedSent() {
    UUID aggregateId = UUID.randomUUID();
    transaction.executeWithoutResult(status -> outbox.add(event(aggregateId)));
    UUID eventId =
        jdbc.queryForObject(
            "SELECT id FROM outbox_events WHERE aggregate_id = ?", UUID.class, aggregateId);

    Message message = receive(TEST_QUEUE, eventId.toString(), Duration.ofSeconds(10));

    MessageProperties properties = message.getMessageProperties();
    assertThat(properties.getType()).isEqualTo("Ping");
    assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(
            jsonMapper.readValue(
                new String(message.getBody(), StandardCharsets.UTF_8),
                new TypeReference<Map<String, Object>>() {}))
        .containsEntry("word", "bee");
    await().atMost(Duration.ofSeconds(5)).until(() -> sentAt(eventId) != null);
  }

  @Test
  void aRolledBackTransactionLeavesNoEvent() {
    UUID aggregateId = UUID.randomUUID();

    transaction.executeWithoutResult(
        status -> {
          outbox.add(event(aggregateId));
          status.setRollbackOnly();
        });

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class,
                aggregateId))
        .isZero();
  }

  @Test
  void addingNeedsTheCallersTransaction() {
    assertThatThrownBy(() -> outbox.add(event(UUID.randomUUID())))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void aFailedMessageComesBackToItsQueueAfterItsDelay() {
    String id = UUID.randomUUID().toString();
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(id);
    Duration delay = Queues.RETRY_DELAYS.getFirst();
    long start = System.nanoTime();

    rabbit.send(
        "",
        Queues.retry(TEST_QUEUE, delay),
        new Message("{}".getBytes(StandardCharsets.UTF_8), properties));

    receive(TEST_QUEUE, id, Duration.ofSeconds(10));
    assertThat(Duration.ofNanos(System.nanoTime() - start))
        .isGreaterThanOrEqualTo(delay.minusMillis(500));
  }

  @Test
  void sentEventsAreDeletedAfterAWeek() {
    Instant now = clock.instant();
    UUID old = insertSent(now.minus(Duration.ofDays(8)));
    UUID recent = insertSent(now.minus(Duration.ofDays(6)));

    outbox.deleteOldSent();

    assertThat(exists(old)).isFalse();
    assertThat(exists(recent)).isTrue();
  }

  private static NewOutboxEvent event(UUID aggregateId) {
    return new NewOutboxEvent("TEST", aggregateId, "Ping", TEST_QUEUE, Map.of("word", "bee"));
  }

  private Message receive(String queue, String messageId, Duration timeout) {
    return RabbitTestSupport.receive(rabbit, queue, messageId, timeout);
  }

  private Instant sentAt(UUID eventId) {
    OffsetDateTime sentAt =
        jdbc.queryForObject(
            "SELECT sent_at FROM outbox_events WHERE id = ?", OffsetDateTime.class, eventId);
    return sentAt == null ? null : sentAt.toInstant();
  }

  private UUID insertSent(Instant sentAt) {
    UUID id = UUID.randomUUID();
    OffsetDateTime at = OffsetDateTime.ofInstant(sentAt, ZoneOffset.UTC);
    jdbc.update(
        "INSERT INTO outbox_events"
            + " (id, aggregate_type, aggregate_id, event_type, routing_key, payload, created_at,"
            + " sent_at)"
            + " VALUES (?, 'TEST', ?, 'Ping', ?, '{}'::jsonb, ?, ?)",
        id,
        UUID.randomUUID(),
        TEST_QUEUE,
        at,
        at);
    return id;
  }

  private boolean exists(UUID id) {
    Integer count =
        jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE id = ?", Integer.class, id);
    return count != null && count > 0;
  }
}
