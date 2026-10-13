package app.ari.api.feature.mail.domain.model;

/** An email ready to send: the subject, and the body as HTML and as plain text. */
public record RenderedEmail(String subject, String html, String text) {}
