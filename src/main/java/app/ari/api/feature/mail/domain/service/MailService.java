package app.ari.api.feature.mail.domain.service;

import java.util.Map;

/** Sends queued emails, each event once. */
public interface MailService {

  /**
   * Renders and sends the email for this event; false when it was already sent. Throws {@code
   * UnprocessableMessageException} for an unknown type or missing values.
   */
  boolean deliver(String eventId, String type, Map<String, Object> data);
}
