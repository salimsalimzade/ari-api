package app.ari.api.feature.mail.domain.port;

import app.ari.api.feature.mail.domain.model.RenderedEmail;

/** Hands an email to the mail server; throws when the server doesn't take it. */
public interface EmailSender {

  void send(String to, RenderedEmail email);
}
