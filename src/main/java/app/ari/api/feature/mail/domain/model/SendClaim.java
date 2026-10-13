package app.ari.api.feature.mail.domain.model;

/** What a mail sender found when it tried to claim an event before sending it. */
public enum SendClaim {
  /** Nobody sent it yet; this sender may. */
  CLAIMED,
  /** It was sent before; a repeated delivery. */
  ALREADY_SENT,
  /** Another delivery is sending it right now, or crashed less than a minute ago. */
  IN_PROGRESS
}
