package app.ari.api.feature.mail.infrastructure;

import app.ari.api.config.MailSenderProperties;
import app.ari.api.feature.mail.domain.model.RenderedEmail;
import app.ari.api.feature.mail.domain.port.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/** SMTP adapter for {@link EmailSender}: HTML with a plain-text alternative. */
@Component
public class SmtpEmailSender implements EmailSender {

  private final JavaMailSender mailSender;
  private final MailSenderProperties properties;

  public SmtpEmailSender(JavaMailSender mailSender, MailSenderProperties properties) {
    this.mailSender = mailSender;
    this.properties = properties;
  }

  @Override
  public void send(String to, RenderedEmail email) {
    MimeMessage message = mailSender.createMimeMessage();
    try {
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
      helper.setFrom(properties.from());
      helper.setTo(to);
      helper.setSubject(email.subject());
      helper.setText(email.text(), email.html());
    } catch (MessagingException e) {
      throw new MailPreparationException("Could not build the email", e);
    }
    mailSender.send(message);
  }
}
