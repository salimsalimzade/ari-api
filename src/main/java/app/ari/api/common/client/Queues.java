package app.ari.api.common.client;

import java.time.Duration;
import java.util.List;

/**
 * RabbitMQ names and retry delays. Every work queue is bound to the {@link #EXCHANGE} by its own
 * name and has a delay queue per retry and a dead-letter queue; config/RabbitConfig declares them.
 */
public final class Queues {

  /** The direct exchange every work queue is bound to. */
  public static final String EXCHANGE = "ari";

  /** Emails a user is waiting for, such as a verify link. */
  public static final String EMAIL_TRANSACTIONAL = "email.transactional";

  /** How long a failed message waits before each retry; after the last one it is dead-lettered. */
  public static final List<Duration> RETRY_DELAYS =
      List.of(Duration.ofSeconds(2), Duration.ofSeconds(10), Duration.ofSeconds(60));

  private Queues() {}

  /** e.g. {@code email.transactional.retry.10s} */
  public static String retry(String queue, Duration delay) {
    return queue + ".retry." + delay.toSeconds() + "s";
  }

  /** e.g. {@code email.transactional.dlq} */
  public static String deadLetter(String queue) {
    return queue + ".dlq";
  }
}
