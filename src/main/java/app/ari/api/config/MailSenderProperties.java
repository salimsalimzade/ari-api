package app.ari.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The emails Ari sends ({@code app.mail}); the SMTP server itself is {@code spring.mail}.
 *
 * @param from the sender, e.g. {@code Ari <no-reply@localhost>}, from {@code APP_MAIL_FROM}
 */
@Validated
@ConfigurationProperties("app.mail")
public record MailSenderProperties(@NotBlank(message = "set APP_MAIL_FROM") String from) {}
