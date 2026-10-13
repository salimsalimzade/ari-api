package app.ari.api.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The website ({@code app.web}).
 *
 * @param url where it runs, for links in emails, from {@code APP_WEB_URL}
 */
@Validated
@ConfigurationProperties("app.web")
public record WebProperties(@NotBlank(message = "set APP_WEB_URL") String url) {}
