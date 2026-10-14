package app.ari.api.feature.note.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import app.ari.api.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The Notes page's endpoints on real PostgreSQL, quoting saves made through the Library's. */
@IntegrationTest
class NoteControllerTest {

  private static final String PAGE = "https://meadow.example/honeybees";

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;

  // — Writing —

  @Test
  void startsAnEmptyNote() {
    Account account = signUp();

    MvcTestResult result = create(account, note("", "{\"kind\": \"TEXT\", \"text\": \"\"}"));

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(read(result, "$.id")).isNotNull();
    assertThat(read(result, "$.title")).isEqualTo("");
    assertThat(JsonPath.<List<String>>read(body(result), "$.blocks[*].kind"))
        .containsExactly("TEXT");
    assertThat(read(result, "$.blocks[0].text")).isEqualTo("");
    assertThat(read(result, "$.updatedAt")).isEqualTo(read(result, "$.createdAt"));
    MvcTestResult listed = list(account);
    assertThat(read(listed, "$[0].id")).isEqualTo(read(result, "$.id"));
    assertThat(read(listed, "$[0].empty")).isEqualTo(true);
    assertThat(JsonPath.<List<String>>read(body(listed), "$[0].saveIds")).isEmpty();
  }

  @Test
  void savesTheWholeNoteWithEachQuotesSaveAsItIsNow() {
    Account account = signUp();
    String saveId = saveHighlight(account, "In late spring a colony splits in two.");
    String id =
        text(
            create(
                account,
                note(
                    "  Swarm decisions  ",
                    "{\"kind\": \"TEXT\", \"text\": \"Before\"}",
                    quote(saveId),
                    "{\"kind\": \"TEXT\", \"text\": \"\"}")),
            "$.id");
    mvc.patch()
        .uri("/api/v1/saves/" + saveId)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"color\": \"PINK\"}")
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();

    MvcTestResult found = get(account, id);

    assertThat(found).hasStatusOk();
    assertThat(read(found, "$.title")).isEqualTo("Swarm decisions");
    assertThat(JsonPath.<List<String>>read(body(found), "$.blocks[*].kind"))
        .containsExactly("TEXT", "QUOTE", "TEXT");
    assertThat(read(found, "$.blocks[1].save.id")).isEqualTo(saveId);
    assertThat(read(found, "$.blocks[1].save.text"))
        .isEqualTo("In late spring a colony splits in two.");
    assertThat(read(found, "$.blocks[1].save.color")).isEqualTo("PINK");
    assertThat(read(found, "$.blocks[1].save.source.url")).isEqualTo(PAGE);
    assertThat(body(found)).doesNotContain("\"saveId\"");

    clock.advance(Duration.ofMinutes(5));
    MvcTestResult replaced =
        replace(account, id, note("Rewritten", "{\"kind\": \"TEXT\", \"text\": \"Only this\"}"));

    assertThat(replaced).hasStatusOk();
    assertThat(JsonPath.<List<String>>read(body(get(account, id)), "$.blocks[*].text"))
        .containsExactly("Only this");
    assertThat(Instant.parse(text(replaced, "$.updatedAt")))
        .isAfter(Instant.parse(text(replaced, "$.createdAt")));
  }

  @Test
  void listsTheNotesChangedMostRecentlyFirstWithTheSavesTheyQuote() {
    Account account = signUp();
    String first = saveHighlight(account, "first");
    String second = saveHighlight(account, "second");
    String older = text(create(account, note("Older", quote(second), quote(first))), "$.id");
    clock.advance(Duration.ofSeconds(1));
    String blank =
        text(create(account, note("", "{\"kind\": \"TEXT\", \"text\": \" \\n \"}")), "$.id");
    clock.advance(Duration.ofSeconds(1));
    replace(account, older, note("Older, edited", quote(second), quote(first)));

    MvcTestResult listed = list(account);

    assertThat(JsonPath.<List<String>>read(body(listed), "$[*].id")).containsExactly(older, blank);
    assertThat(JsonPath.<List<String>>read(body(listed), "$[0].saveIds"))
        .containsExactly(second, first);
    assertThat(read(listed, "$[0].empty")).isEqualTo(false);
    // Only whitespace counts as nothing written.
    assertThat(read(listed, "$[1].empty")).isEqualTo(true);
  }

  @Test
  void aQuoteOfASaveThatIsntTheUsersIsLeftOut() {
    Account ada = signUp();
    Account grace = signUp();
    String graces = saveHighlight(grace, "grace's own");
    String adas = saveHighlight(ada, "ada's own");

    MvcTestResult created =
        create(ada, note("t", quote(graces), quote(UUID.randomUUID().toString()), quote(adas)));

    assertThat(JsonPath.<List<String>>read(body(created), "$.blocks[*].save.id"))
        .containsExactly(adas);
    assertThat(body(created)).doesNotContain("grace's own");
    assertThat(JsonPath.<List<String>>read(body(list(ada)), "$[0].saveIds")).containsExactly(adas);
  }

  @Test
  void deletingASaveTakesItsQuotesWithIt() {
    Account account = signUp();
    String stays = saveHighlight(account, "stays");
    String goes = saveHighlight(account, "goes");
    String id = text(create(account, note("t", quote(goes), quote(stays))), "$.id");

    mvc.delete()
        .uri("/api/v1/saves/" + goes)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();

    assertThat(JsonPath.<List<String>>read(body(get(account, id)), "$.blocks[*].save.id"))
        .containsExactly(stays);
    assertThat(JsonPath.<List<String>>read(body(list(account)), "$[0].saveIds"))
        .containsExactly(stays);
  }

  @Test
  void refusesABlockThatBreaksItsKindsRules() {
    Account account = signUp();
    String saveId = saveHighlight(account, "t");

    MvcTestResult quoteWithText =
        create(
            account,
            note("t", "{\"kind\": \"QUOTE\", \"saveId\": \"" + saveId + "\", \"text\": \"x\"}"));
    MvcTestResult textWithoutText = create(account, note("t", "{\"kind\": \"TEXT\"}"));
    MvcTestResult noBlocks = create(account, "{\"title\": \"t\"}");
    MvcTestResult longTitle =
        create(account, note("x".repeat(201), "{\"kind\": \"TEXT\", \"text\": \"\"}"));

    assertThat(quoteWithText).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(read(quoteWithText, "$.code")).isEqualTo("INVALID_NOTE");
    assertThat(read(textWithoutText, "$.code")).isEqualTo("INVALID_NOTE");
    assertThat(read(noBlocks, "$.code")).isEqualTo("VALIDATION_FAILED");
    assertThat(read(longTitle, "$.code")).isEqualTo("VALIDATION_FAILED");
    assertThat(JsonPath.<List<Object>>read(body(list(account)), "$")).isEmpty();
  }

  @Test
  void deletesANoteAndKeepsTheSavesItQuotes() {
    Account account = signUp();
    String saveId = saveHighlight(account, "kept");
    String id = text(create(account, note("t", quote(saveId))), "$.id");

    assertThat(delete(account, id)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(get(account, id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(delete(account, id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(
            mvc.get()
                .uri("/api/v1/saves/" + saveId)
                .cookie(new Cookie(ACCESS, account.accessToken()))
                .exchange())
        .hasStatusOk();
  }

  // — Search —

  @Test
  void searchFindsNotesByTheirTitleAndTheirOwnWriting() {
    Account account = signUp();
    String quoted = saveHighlight(account, "The nest site wins");
    String essay =
        text(
            create(
                account,
                note(
                    "Swarm decisions",
                    "{\"kind\": \"TEXT\", \"text\": \"Scouts report back\"}",
                    quote(quoted),
                    "{\"kind\": \"TEXT\", \"text\": \"The quorum is fifteen Ölçü\"}")),
            "$.id");
    String checklist =
        text(
            create(
                account,
                note("Hive checklist", "{\"kind\": \"TEXT\", \"text\": \"Measure the entrance\"}")),
            "$.id");

    assertThat(ids(search(account, "swarm"))).containsExactly(essay);
    // Words from different paragraphs, any case, no accents.
    assertThat(ids(search(account, "SCOUTS olcu"))).containsExactly(essay);
    assertThat(ids(search(account, "entran"))).containsExactly(checklist);
    // Part of a word.
    assertThat(ids(search(account, "eckli"))).containsExactly(checklist);
    // A quote's words are the save's, not the note's.
    assertThat(ids(search(account, "nest"))).isEmpty();
    assertThat(ids(search(account, " "))).containsExactlyInAnyOrder(essay, checklist);

    replace(
        account,
        checklist,
        note("Hive checklist", "{\"kind\": \"TEXT\", \"text\": \"Bottom board\"}"));

    assertThat(ids(search(account, "entrance"))).isEmpty();
    assertThat(ids(search(account, "bottom"))).containsExactly(checklist);
    assertThat(search(account, "x".repeat(201))).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void searchOnlyEverFindsTheSignedInUsersNotes() {
    Account ada = signUp();
    Account grace = signUp();
    create(ada, note("Private swarm", "{\"kind\": \"TEXT\", \"text\": \"\"}"));

    assertThat(ids(search(grace, "swarm"))).isEmpty();
  }

  // — Whose notes —

  @Test
  void aNoteIsOnlyEverTheSignedInUsers() {
    Account ada = signUp();
    Account grace = signUp();
    String id =
        text(create(ada, note("Ada's", "{\"kind\": \"TEXT\", \"text\": \"mine\"}")), "$.id");

    assertThat(get(grace, id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(replace(grace, id, note("Grace's now"))).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(delete(grace, id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(JsonPath.<List<Object>>read(body(list(grace)), "$")).isEmpty();
    assertThat(read(get(ada, id), "$.title")).isEqualTo("Ada's");
  }

  @Test
  void needsASignedInUser() {
    assertThat(mvc.get().uri("/api/v1/notes").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/api/v1/notes/" + UUID.randomUUID()).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(
            mvc.post()
                .uri("/api/v1/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(note("t"))
                .exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void theOpenApiDescriptionShowsTheNotesEndpoints() {
    MvcTestResult docs = mvc.get().uri("/v3/api-docs").exchange();

    assertThat(docs).hasStatusOk();
    String json = body(docs);
    assertThat(JsonPath.<List<String>>read(json, "$.paths['/api/v1/notes'].get.parameters[*].name"))
        .containsExactly("q");
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/notes'].post")).isNotNull();
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/notes/{id}'].get")).isNotNull();
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/notes/{id}'].put")).isNotNull();
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/notes/{id}'].delete")).isNotNull();
  }

  // — Helpers —

  private record Account(UUID id, String accessToken) {}

  private Account signUp() {
    String username = "note_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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
    UUID id = jdbc.queryForObject("SELECT id FROM users WHERE username = ?", UUID.class, username);
    return new Account(id, access.getValue());
  }

  /** Saves a highlight through the Library; returns its id. */
  private String saveHighlight(Account account, String text) {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/saves")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"kind": "HIGHLIGHT", "color": "YELLOW", "text": "%s",
                 "source": {"url": "%s", "name": "Meadow Quarterly", "title": "T"}}
                """
                    .formatted(text, PAGE))
            .cookie(new Cookie(ACCESS, account.accessToken()))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return text(result, "$.id");
  }

  private static String note(String title, String... blocks) {
    return "{\"title\": \"%s\", \"blocks\": [%s]}".formatted(title, String.join(", ", blocks));
  }

  private static String quote(String saveId) {
    return "{\"kind\": \"QUOTE\", \"saveId\": \"" + saveId + "\"}";
  }

  private MvcTestResult create(Account account, String json) {
    return mvc.post()
        .uri("/api/v1/notes")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult replace(Account account, String id, String json) {
    return mvc.put()
        .uri("/api/v1/notes/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult list(Account account) {
    return mvc.get()
        .uri("/api/v1/notes")
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult search(Account account, String q) {
    return mvc.get()
        .uri("/api/v1/notes")
        .param("q", q)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private static List<String> ids(MvcTestResult result) {
    assertThat(result).hasStatusOk();
    return JsonPath.read(body(result), "$[*].id");
  }

  private MvcTestResult get(Account account, String id) {
    return mvc.get()
        .uri("/api/v1/notes/" + id)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult delete(Account account, String id) {
    return mvc.delete()
        .uri("/api/v1/notes/" + id)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Object read(MvcTestResult result, String path) {
    return JsonPath.read(body(result), path);
  }

  private static String text(MvcTestResult result, String path) {
    return JsonPath.read(body(result), path);
  }
}
