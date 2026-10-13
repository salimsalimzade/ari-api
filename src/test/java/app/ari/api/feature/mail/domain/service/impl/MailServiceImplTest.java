package app.ari.api.feature.mail.domain.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ari.api.common.client.UnprocessableMessageException;
import app.ari.api.feature.mail.domain.model.RenderedEmail;
import app.ari.api.feature.mail.domain.model.SendClaim;
import app.ari.api.feature.mail.domain.repository.SentEmailRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

/** Sending each event once, without Spring, Redis or SMTP. */
class MailServiceImplTest {

  private final InMemorySentEmails sentEmails = new InMemorySentEmails();
  private final List<String> sent = new ArrayList<>();
  private boolean smtpDown;

  private final MailServiceImpl mail =
      new MailServiceImpl(
          sentEmails,
          (template, language, values) ->
              new RenderedEmail(template + " in " + language, "<p>html</p>", "text"),
          (to, email) -> {
            if (smtpDown) {
              throw new MailSendException("SMTP is down");
            }
            sent.add(to + ": " + email.subject());
          });

  @Test
  void sendsTheEmailInTheJobsLanguage() {
    assertThat(mail.deliver("e1", "VerifyEmail", data("az"))).isTrue();

    assertThat(sent).containsExactly("bee@example.com: verify-email in az");
  }

  @Test
  void anyLanguageButAzerbaijaniGetsEnglish() {
    mail.deliver("e1", "VerifyEmail", data("fr"));

    assertThat(sent).containsExactly("bee@example.com: verify-email in en");
  }

  @Test
  void aRepeatedEventIsNotSentAgain() {
    mail.deliver("e1", "VerifyEmail", data("en"));

    assertThat(mail.deliver("e1", "VerifyEmail", data("en"))).isFalse();
    assertThat(sent).hasSize(1);
  }

  @Test
  void aFailedSendLetsTheRetrySend() {
    smtpDown = true;
    assertThatThrownBy(() -> mail.deliver("e1", "VerifyEmail", data("en")))
        .isInstanceOf(MailSendException.class);

    smtpDown = false;
    assertThat(mail.deliver("e1", "VerifyEmail", data("en"))).isTrue();
    assertThat(sent).hasSize(1);
  }

  @Test
  void anEventBeingSentElsewhereIsRetriedLater() {
    sentEmails.claim("e1");

    assertThatThrownBy(() -> mail.deliver("e1", "VerifyEmail", data("en")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(sent).isEmpty();
  }

  @Test
  void unknownTypesAndMissingRecipientsCanNeverBeSent() {
    assertThatThrownBy(() -> mail.deliver("e1", "NoSuchEmail", data("en")))
        .isInstanceOf(UnprocessableMessageException.class);
    assertThatThrownBy(() -> mail.deliver("e2", "VerifyEmail", Map.of("language", "en")))
        .isInstanceOf(UnprocessableMessageException.class);
    assertThat(sent).isEmpty();
  }

  private static Map<String, Object> data(String language) {
    return Map.of("to", "bee@example.com", "fullName", "Bee", "language", language);
  }

  /** Same rules as the Redis adapter, without the expiry. */
  private static final class InMemorySentEmails implements SentEmailRepository {

    private final Map<String, String> states = new HashMap<>();

    @Override
    public SendClaim claim(String eventId) {
      String state = states.putIfAbsent(eventId, "sending");
      if (state == null) {
        return SendClaim.CLAIMED;
      }
      return state.equals("sent") ? SendClaim.ALREADY_SENT : SendClaim.IN_PROGRESS;
    }

    @Override
    public void markSent(String eventId) {
      states.put(eventId, "sent");
    }

    @Override
    public void release(String eventId) {
      states.remove(eventId);
    }
  }
}
