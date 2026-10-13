package app.ari.api.feature.mail.domain.port;

import app.ari.api.feature.mail.domain.model.RenderedEmail;
import java.util.Map;

/** Turns a template and its values into an email in one language. */
public interface EmailRenderer {

  RenderedEmail render(String template, String language, Map<String, Object> values);
}
