package app.ari.api.feature.mail.infrastructure;

import app.ari.api.feature.mail.domain.model.RenderedEmail;
import app.ari.api.feature.mail.domain.port.EmailRenderer;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders {@code templates/mail/{template}.html} and {@code .txt} with Thymeleaf. The texts and the
 * subject ({@code {template}.subject}) come from {@code i18n/mail.properties} and {@code
 * mail_az.properties}.
 */
@Component
public class ThymeleafEmailRenderer implements EmailRenderer {

  private final ITemplateEngine templates;
  private final MessageSource messages;

  public ThymeleafEmailRenderer(ITemplateEngine templates, MessageSource messages) {
    this.templates = templates;
    this.messages = messages;
  }

  @Override
  public RenderedEmail render(String template, String language, Map<String, Object> values) {
    Locale locale = Locale.forLanguageTag(language);
    Context context = new Context(locale, values);
    return new RenderedEmail(
        messages.getMessage(template + ".subject", null, locale),
        templates.process("mail/" + template, context),
        templates.process("mail/" + template + ".txt", context));
  }
}
