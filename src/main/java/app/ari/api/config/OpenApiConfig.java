package app.ari.api.config;

import app.ari.api.feature.account.presentation.cookie.AuthCookies;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  private static final String COOKIE_AUTH = "cookieAuth";
  private static final String BEARER_AUTH = "bearerAuth";

  /** Every endpoint needs one of the two schemes unless it says otherwise. */
  @Bean
  public OpenAPI ariOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Ari API")
                .version("v1")
                .description("Saves highlights and clipped page sections with their sources."))
        .components(
            new Components()
                .addSecuritySchemes(
                    COOKIE_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name(AuthCookies.ACCESS)
                        .description("Set by sign-in; the browser sends it automatically"))
                .addSecuritySchemes(
                    BEARER_AUTH,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("For the browser extension")))
        .addSecurityItem(new SecurityRequirement().addList(COOKIE_AUTH))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
  }
}
