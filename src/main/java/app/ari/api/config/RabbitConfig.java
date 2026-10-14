package app.ari.api.config;

import app.ari.api.common.client.Queues;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The RabbitMQ queues, declared at startup. A consumer that fails moves the message to the next
 * delay queue, which sends it back to the work queue when its time is up; after the last delay it
 * goes to the dead-letter queue (docs/architecture.md section 1.1).
 */
@Configuration
public class RabbitConfig {

  private static final List<String> WORK_QUEUES =
      List.of(Queues.EMAIL_TRANSACTIONAL, Queues.IMPORT_PROCESS);

  @Bean
  public DirectExchange ariExchange() {
    return new DirectExchange(Queues.EXCHANGE, true, false);
  }

  @Bean
  public Declarables workQueues(DirectExchange ariExchange) {
    List<Declarable> declarables = new ArrayList<>();
    for (String name : WORK_QUEUES) {
      declarables.addAll(queueWithRetries(name, ariExchange));
    }
    return new Declarables(declarables);
  }

  /** The work queue and its binding, its delay queues and its dead-letter queue. */
  public static List<Declarable> queueWithRetries(String name, DirectExchange exchange) {
    Queue work = QueueBuilder.durable(name).build();
    List<Declarable> declarables = new ArrayList<>();
    declarables.add(work);
    declarables.add(BindingBuilder.bind(work).to(exchange).with(name));
    for (Duration delay : Queues.RETRY_DELAYS) {
      declarables.add(
          QueueBuilder.durable(Queues.retry(name, delay))
              .ttl(Math.toIntExact(delay.toMillis()))
              .deadLetterExchange(exchange.getName())
              .deadLetterRoutingKey(name)
              .build());
    }
    declarables.add(QueueBuilder.durable(Queues.deadLetter(name)).build());
    return declarables;
  }
}
