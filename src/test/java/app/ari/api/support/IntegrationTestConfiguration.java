package app.ari.api.support;

import java.time.Instant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real PostgreSQL, Redis, RabbitMQ, Mailpit and MinIO (Docker must be running) and a clock the
 * tests control.
 */
@TestConfiguration(proxyBeanMethods = false)
public class IntegrationTestConfiguration {

  public static final int MAILPIT_SMTP = 1025;
  public static final int MAILPIT_API = 8025;

  @Bean
  @ServiceConnection
  public PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:18");
  }

  /** A plain container, so Boot needs the name to know it is Redis. */
  @Bean
  @ServiceConnection(name = "redis")
  public GenericContainer<?> redis() {
    return new GenericContainer<>("redis:8").withExposedPorts(6379);
  }

  @Bean
  @ServiceConnection
  public RabbitMQContainer rabbitmq() {
    return new RabbitMQContainer("rabbitmq:4");
  }

  /** Catches every email and accepts any SMTP login; tests read the emails through its API. */
  @Bean
  public GenericContainer<?> mailpit() {
    return new GenericContainer<>("axllent/mailpit:v1")
        .withEnv("MP_SMTP_AUTH_ACCEPT_ANY", "true")
        .withEnv("MP_SMTP_AUTH_ALLOW_INSECURE", "true")
        .withExposedPorts(MAILPIT_SMTP, MAILPIT_API);
  }

  @Bean
  public DynamicPropertyRegistrar mailpitProperties(
      @Qualifier("mailpit") GenericContainer<?> mailpit) {
    return registry -> {
      registry.add("spring.mail.host", mailpit::getHost);
      registry.add("spring.mail.port", () -> mailpit.getMappedPort(MAILPIT_SMTP));
    };
  }

  /** The same MinIO fork as docker-compose.yml. */
  @Bean
  public MinIOContainer minio() {
    return new MinIOContainer(
        DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z")
            .asCompatibleSubstituteFor("minio/minio"));
  }

  @Bean
  public DynamicPropertyRegistrar minioProperties(MinIOContainer minio) {
    return registry -> {
      registry.add("app.storage.endpoint", minio::getS3URL);
      registry.add("app.storage.public-endpoint", minio::getS3URL);
      registry.add("app.storage.access-key", minio::getUserName);
      registry.add("app.storage.secret-key", minio::getPassword);
    };
  }

  /** Starts at a fixed time; tests move it forward to expire tokens. */
  @Bean
  @Primary
  public MutableClock testClock() {
    return new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
  }
}
