package app.ari.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI ariOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Ari API")
                .version("v1")
                .description("Saves highlights and clipped page sections with their sources."));
  }
}
