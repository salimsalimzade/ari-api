package app.ari.api.feature.mail.domain.service.impl;

import app.ari.api.common.client.UnprocessableMessageException;
import app.ari.api.feature.mail.domain.port.EmailRenderer;
import app.ari.api.feature.mail.domain.port.EmailSender;
import app.ari.api.feature.mail.domain.repository.SentEmailRepository;
import app.ari.api.feature.mail.domain.service.MailService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MailServiceImpl implements MailService {

  /**
   * Each event type and its template in templates/mail/, with its texts in i18n/mail*.properties.
   */
  private static final Map<String, String> TEMPLATES = Map.of("VerifyEmail", "verify-email");

  private final SentEmailRepository sentEmails;
  private final EmailRenderer renderer;
  private final EmailSender sender;

  public MailServiceImpl(
      SentEmailRepository sentEmails, EmailRenderer renderer, EmailSender sender) {
    this.sentEmails = sentEmails;
    this.renderer = renderer;
    this.sender = sender;
  }

  @Override
  public boolean deliver(String eventId, String type, Map<String, Object> data) {
    String template = TEMPLATES.get(type);
    if (template == null) {
      throw new UnprocessableMessageException("No email template for " + type);
    }
    if (!(data.get("to") instanceof String to) || to.isBlank()) {
      throw new UnprocessableMessageException(type + " has no recipient");
    }
    String language = "az".equals(data.get("language")) ? "az" : "en";
    switch (sentEmails.claim(eventId)) {
      case ALREADY_SENT -> {
        return false;
      }
      case IN_PROGRESS -> throw new IllegalStateException("Event " + eventId + " is being sent");
      case CLAIMED -> {}
    }
    try {
      sender.send(to, renderer.render(template, language, data));
    } catch (RuntimeException e) {
      sentEmails.release(eventId);
      throw e;
    }
    sentEmails.markSent(eventId);
    return true;
  }
}
