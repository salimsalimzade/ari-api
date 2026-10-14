package app.ari.api.feature.outbox.presentation.scheduler;

import app.ari.api.feature.outbox.application.facade.OutboxFacade;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the outbox on a timer: sends new events every second and deletes old ones every night. */
@Component
public class OutboxScheduler {

  private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

  private final OutboxFacade outbox;

  public OutboxScheduler(OutboxFacade outbox) {
    this.outbox = outbox;
  }

  /** A failure leaves the events unsent, so the next run tries them again. */
  @Scheduled(fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
  public void publishPending() {
    try {
      outbox.publishPending();
    } catch (RuntimeException e) {
      log.warn("Outbox: sending to RabbitMQ failed, trying again in a second: {}", e.getMessage());
    }
  }

  @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
  public void deleteOldSent() {
    log.info("Outbox: deleted {} events sent over a week ago", outbox.deleteOldSent());
    log.info(
        "Outbox: deleted {} records of events handled over 30 days ago",
        outbox.deleteOldProcessed());
  }
}
