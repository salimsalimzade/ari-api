package app.ari.api.feature.mail;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import app.ari.api.common.client.FailedMessageHandler;
import app.ari.api.common.client.Queues;
import app.ari.api.feature.account.presentation.AccountController;
import app.ari.api.support.IntegrationTest;
import app.ari.api.support.MailpitInbox;
import app.ari.api.support.MailpitInbox.CaughtEmail;
import app.ari.api.support.RabbitTestSupport;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.json.JsonMapper;

/** The whole way: the API queues an email, RabbitMQ carries it, Mailpit catches it. */
@IntegrationTest
class MailTest {

  private static final Pattern LINK =
      Pattern.compile("http://localhost:3000/verify-email\\?code=[A-Za-z0-9_-]+");
  private static final Duration DELIVERY = Duration.ofSeconds(15);

  @Autowired private MockMvcTester mvc;
  @Autowired private RabbitTemplate rabbit;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private JavaMailSenderImpl mailSender;

  @Autowired
  @Qualifier("mailpit") private GenericContainer<?> mailpit;

  private MailpitInbox inbox;

  @BeforeEach
  void openTheInbox() {
    inbox = new MailpitInbox(mailpit, jsonMapper);
  }

  @Test
  void addingAnEmailSendsItsVerifyLinkInTheUsersLanguage() {
    String accessToken = signUp();
    String email = newEmail();

    assertThat(changeEmail(accessToken, email, "az")).hasStatus(HttpStatus.ACCEPTED);

    CaughtEmail caught = awaitOneEmail(email);
    assertThat(caught.subject()).isEqualTo("Ari üçün e-poçtunuzu təsdiqləyin");
    assertThat(caught.from()).isEqualTo("Ari <no-reply@ari.test>");
    assertThat(caught.text()).contains("Salam, Test User!");
    String link = linkIn(caught.text());
    assertThat(caught.html()).contains("href=\"" + link + "\"").contains("E-poçtu təsdiqlə");

    String code = link.substring(link.indexOf("code=") + "code=".length());
    assertThat(
            mvc.post()
                .uri("/api/v1/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\": \"%s\"}".formatted(code))
                .exchange())
        .hasStatus(HttpStatus.NO_CONTENT);
  }

  @Test
  void theEmailIsInEnglishUnlessTheLanguageIsAzerbaijani() {
    String email = newEmail();

    changeEmail(signUp(), email, "fr");

    CaughtEmail caught = awaitOneEmail(email);
    assertThat(caught.subject()).isEqualTo("Confirm your email for Ari");
    assertThat(caught.text()).contains("Hi Test User,").contains("expires in 24 hours");
    assertThat(caught.html()).contains("Confirm email").contains("lang=\"en\"");
  }

  @Test
  void aRepeatedJobIsSentOnce() {
    String email = newEmail();
    Message job = job(UUID.randomUUID().toString(), "VerifyEmail", verifyEmailData(email));

    rabbit.send(Queues.EXCHANGE, Queues.EMAIL_TRANSACTIONAL, job);
    rabbit.send(Queues.EXCHANGE, Queues.EMAIL_TRANSACTIONAL, job);

    awaitOneEmail(email);
    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .until(() -> inbox.to(email).size() == 1);
  }

  @Test
  void aJobThatCanNeverBeSentGoesStraightToTheDeadLetterQueue() {
    String id = UUID.randomUUID().toString();

    rabbit.send(
        Queues.EXCHANGE,
        Queues.EMAIL_TRANSACTIONAL,
        job(id, "NoSuchEmail", verifyEmailData(newEmail())));

    Message dead =
        RabbitTestSupport.receive(
            rabbit, Queues.deadLetter(Queues.EMAIL_TRANSACTIONAL), id, Duration.ofSeconds(10));
    MessageProperties properties = dead.getMessageProperties();
    assertThat((String) properties.getHeader(FailedMessageHandler.ERROR_HEADER))
        .contains("NoSuchEmail");
    assertThat((Object) properties.getHeader(FailedMessageHandler.ATTEMPT_HEADER)).isNull();
  }

  @Test
  void theSmtpSettingsReachTheMailSender() {
    assertThat(mailSender.getJavaMailProperties())
        .containsEntry("mail.smtp.starttls.enable", "false")
        .containsEntry("mail.smtp.ssl.enable", "false")
        .containsEntry("mail.smtp.timeout", "10000");
    assertThat(mailSender.getUsername()).isEqualTo("ari-test");
  }

  private CaughtEmail awaitOneEmail(String address) {
    await().atMost(DELIVERY).until(() -> !inbox.to(address).isEmpty());
    List<CaughtEmail> emails = inbox.to(address);
    assertThat(emails).hasSize(1);
    return emails.getFirst();
  }

  private static String linkIn(String text) {
    Matcher matcher = LINK.matcher(text);
    assertThat(matcher.find()).as("a verify link in %s", text).isTrue();
    return matcher.group();
  }

  private String signUp() {
    String username = "post_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"username": "%s", "password": "correct horse", "fullName": "Test User",
                 "birthDate": "1990-05-17"}
                """
                    .formatted(username))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    Cookie access = result.getResponse().getCookie(ACCESS);
    assertThat(access).isNotNull();
    return access.getValue();
  }

  private MvcTestResult changeEmail(String accessToken, String email, String language) {
    return mvc.put()
        .uri("/api/v1/me/email")
        .cookie(
            new Cookie(ACCESS, accessToken),
            new Cookie(AccountController.LANGUAGE_COOKIE, language))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"email\": \"%s\"}".formatted(email))
        .exchange();
  }

  private Message job(String id, String type, Map<String, Object> data) {
    MessageProperties properties = new MessageProperties();
    properties.setMessageId(id);
    properties.setType(type);
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    return new Message(jsonMapper.writeValueAsBytes(data), properties);
  }

  private static Map<String, Object> verifyEmailData(String email) {
    return Map.of(
        "to",
        email,
        "fullName",
        "Bee",
        "language",
        "en",
        "link",
        "http://localhost:3000/verify-email?code=test");
  }

  private static String newEmail() {
    return "mail." + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
  }
}
