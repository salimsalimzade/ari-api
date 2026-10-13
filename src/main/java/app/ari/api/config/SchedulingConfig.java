package app.ari.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs, such as the outbox publisher. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
