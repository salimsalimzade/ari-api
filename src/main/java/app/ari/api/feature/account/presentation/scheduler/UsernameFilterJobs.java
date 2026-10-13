package app.ari.api.feature.account.presentation.scheduler;

import app.ari.api.feature.account.application.facade.AccountFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the username Bloom filter in step with the users table: filled at startup when Redis lost
 * it, and built again every night, which drops usernames nobody has any more.
 */
@Component
public class UsernameFilterJobs {

  private static final Logger log = LoggerFactory.getLogger(UsernameFilterJobs.class);

  private final AccountFacade accounts;

  public UsernameFilterJobs(AccountFacade accounts) {
    this.accounts = accounts;
  }

  /** Without Redis the app still starts: checks ask the database until the next run. */
  @EventListener(ApplicationReadyEvent.class)
  public void fillIfMissing() {
    try {
      int count = accounts.fillUsernameFilterIfMissing();
      if (count >= 0) {
        log.info("Usernames: filled the Bloom filter with {} usernames", count);
      }
    } catch (RuntimeException e) {
      log.warn("Usernames: could not fill the Bloom filter: {}", e.getMessage());
    }
  }

  @Scheduled(cron = "0 30 3 * * *", zone = "UTC")
  public void rebuild() {
    log.info(
        "Usernames: rebuilt the Bloom filter with {} usernames", accounts.rebuildUsernameFilter());
  }
}
