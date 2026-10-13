package app.ari.api.feature.mail.domain.repository;

import app.ari.api.feature.mail.domain.model.SendClaim;

/**
 * Remembers which events were sent, so a repeated delivery sends nothing. A claim lasts a minute,
 * so a sender that crashed mid-send doesn't block the event for long.
 */
public interface SentEmailRepository {

  SendClaim claim(String eventId);

  /** Keeps the event as sent for a week, longer than any repeated delivery. */
  void markSent(String eventId);

  /** Drops the claim after a failed send, so the retry may send. */
  void release(String eventId);
}
