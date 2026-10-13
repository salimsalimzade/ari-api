package app.ari.api.support;

import java.util.ArrayList;
import java.util.List;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The emails the test Mailpit caught, read through its HTTP API. */
public final class MailpitInbox {

  /** One caught email; {@code from} is the sender's name and address, e.g. {@code Ari <a@b>}. */
  public record CaughtEmail(String subject, String from, String text, String html) {}

  private final RestClient http;
  private final JsonMapper jsonMapper;

  public MailpitInbox(GenericContainer<?> mailpit, JsonMapper jsonMapper) {
    this.http =
        RestClient.create(
            "http://"
                + mailpit.getHost()
                + ":"
                + mailpit.getMappedPort(IntegrationTestConfiguration.MAILPIT_API));
    this.jsonMapper = jsonMapper;
  }

  /** Every email sent to this address, newest first. */
  public List<CaughtEmail> to(String address) {
    JsonNode found = get("/api/v1/search?query={query}", "to:" + address);
    List<CaughtEmail> emails = new ArrayList<>();
    for (JsonNode summary : found.get("messages")) {
      JsonNode email = get("/api/v1/message/{id}", summary.get("ID").asString());
      JsonNode from = email.get("From");
      emails.add(
          new CaughtEmail(
              email.get("Subject").asString(),
              from.get("Name").asString() + " <" + from.get("Address").asString() + ">",
              email.get("Text").asString(),
              email.get("HTML").asString()));
    }
    return emails;
  }

  private JsonNode get(String uri, Object variable) {
    return jsonMapper.readTree(http.get().uri(uri, variable).retrieve().body(String.class));
  }
}
