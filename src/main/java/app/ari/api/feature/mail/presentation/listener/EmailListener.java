package app.ari.api.feature.mail.presentation.listener;

import app.ari.api.common.client.FailedMessageHandler;
import app.ari.api.common.client.Queues;
import app.ari.api.common.client.UnprocessableMessageException;
import app.ari.api.feature.mail.application.facade.MailFacade;
import app.ari.api.feature.mail.application.model.message.EmailJob;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Takes email jobs off {@code email.transactional}; a failed one is retried or dead-lettered. */
@Component
public class EmailListener {

  private final MailFacade mail;
  private final FailedMessageHandler failures;
  private final JsonMapper jsonMapper;

  public EmailListener(MailFacade mail, FailedMessageHandler failures, JsonMapper jsonMapper) {
    this.mail = mail;
    this.failures = failures;
    this.jsonMapper = jsonMapper;
  }

  @RabbitListener(queues = Queues.EMAIL_TRANSACTIONAL)
  public void onEmail(Message message) {
    try {
      mail.deliver(toJob(message));
    } catch (RuntimeException e) {
      failures.handle(message, Queues.EMAIL_TRANSACTIONAL, e);
    }
  }

  private EmailJob toJob(Message message) {
    MessageProperties properties = message.getMessageProperties();
    if (properties.getMessageId() == null || properties.getType() == null) {
      throw new UnprocessableMessageException("An email job needs a message id and a type");
    }
    try {
      Map<String, Object> data =
          jsonMapper.readValue(message.getBody(), new TypeReference<Map<String, Object>>() {});
      return new EmailJob(properties.getMessageId(), properties.getType(), data);
    } catch (JacksonException e) {
      throw new UnprocessableMessageException("The body is not a JSON object", e);
    }
  }
}
