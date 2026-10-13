package app.ari.api.feature.mail.application.facade;

import app.ari.api.feature.mail.application.model.message.EmailJob;

/** Sends the emails other features queue on RabbitMQ. */
public interface MailFacade {

  /**
   * Sends the job's email unless it was already sent; returns false for a repeat. Throws {@code
   * UnprocessableMessageException} for a job that can never be sent, and any other exception for
   * one worth retrying.
   */
  boolean deliver(EmailJob job);
}
