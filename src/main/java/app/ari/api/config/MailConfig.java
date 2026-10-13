package app.ari.api.config;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ITemplateResolver;

@Configuration
@EnableConfigurationProperties(MailSenderProperties.class)
public class MailConfig {

  /**
   * Plain-text email bodies: {@code mail/verify-email.txt} resolves here, while {@code
   * mail/verify-email} goes on to Boot's HTML resolver.
   */
  @Bean
  public ITemplateResolver textTemplateResolver() {
    SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
    resolver.setPrefix("classpath:/templates/");
    resolver.setResolvablePatterns(Set.of("*.txt"));
    resolver.setTemplateMode(TemplateMode.TEXT);
    resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
    resolver.setCheckExistence(true);
    resolver.setOrder(0);
    return resolver;
  }
}
