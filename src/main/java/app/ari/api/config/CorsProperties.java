package app.ari.api.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Origins (the frontend) allowed to call the API from a browser. */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {}
