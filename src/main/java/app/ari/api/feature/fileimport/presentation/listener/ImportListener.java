package app.ari.api.feature.fileimport.presentation.listener;

import app.ari.api.common.client.FailedMessageHandler;
import app.ari.api.common.client.Queues;
import app.ari.api.common.client.UnprocessableMessageException;
import app.ari.api.feature.fileimport.application.facade.ImportFacade;
import app.ari.api.feature.fileimport.application.model.message.ImportRequested;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Takes files to read off {@code import.process}. A failure is retried after 2, 10 and 60 seconds;
 * a file that can never be read, or one that failed every time, is marked FAILED and its message
 * dead-lettered.
 */
@Component
public class ImportListener {

  private final ImportFacade imports;
  private final FailedMessageHandler failures;
  private final JsonMapper jsonMapper;

  public ImportListener(
      ImportFacade imports, FailedMessageHandler failures, JsonMapper jsonMapper) {
    this.imports = imports;
    this.failures = failures;
    this.jsonMapper = jsonMapper;
  }

  @RabbitListener(queues = Queues.IMPORT_PROCESS)
  public void onImport(Message message) {
    ImportRequested job;
    try {
      job = toJob(message);
    } catch (RuntimeException e) {
      failures.handle(message, Queues.IMPORT_PROCESS, e);
      return;
    }
    try {
      imports.read(message.getMessageProperties().getMessageId(), job.importId());
    } catch (RuntimeException e) {
      if (failures.handle(message, Queues.IMPORT_PROCESS, e)) {
        imports.giveUp(job.importId(), e);
      }
    }
  }

  private ImportRequested toJob(Message message) {
    if (message.getMessageProperties().getMessageId() == null) {
      throw new UnprocessableMessageException("An import job needs a message id");
    }
    try {
      ImportRequested job = jsonMapper.readValue(message.getBody(), ImportRequested.class);
      if (job.importId() == null) {
        throw new UnprocessableMessageException("An import job needs an importId");
      }
      return job;
    } catch (JacksonException e) {
      throw new UnprocessableMessageException("The body is not an import job", e);
    }
  }
}
