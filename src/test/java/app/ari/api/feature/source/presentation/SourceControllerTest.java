package app.ari.api.feature.source.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import app.ari.api.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The Sources page's endpoints on real PostgreSQL, with saves made through the Library's. */
@IntegrationTest
class SourceControllerTest {

  private static final String MEADOW = "https://meadow.example/honeybees";
  private static final String HIVE = "https://hive.example/queens";

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MutableClock clock;

  // — The list —

  @Test
  void listsEachSourceWithWhatIsSavedFromItTheLatestSavedFromFirst() {
    Account account = signUp();
    Instant first = clock.instant();
    create(account, highlight("old one", "YELLOW", MEADOW, "Meadow Quarterly"));
    clock.advance(Duration.ofSeconds(1));
    create(account, highlight("queens", "PINK", HIVE, "Hive Notes"));
    clock.advance(Duration.ofSeconds(1));
    create(account, highlight("green", "GREEN", MEADOW, "Meadow Quarterly"));
    clock.advance(Duration.ofSeconds(1));
    create(account, highlight("green again", "GREEN", MEADOW, "Meadow Quarterly"));
    clock.advance(Duration.ofSeconds(1));
    create(account, tableClip(MEADOW));

    MvcTestResult result = list(account, "");

    assertThat(result).hasStatusOk();
    assertThat(JsonPath.<List<String>>read(body(result), "$.items[*].name"))
        .containsExactly("Meadow Quarterly", "Hive Notes");
    assertThat(read(result, "$.items[0].kind")).isEqualTo("WEBSITE");
    assertThat(read(result, "$.items[0].url")).isEqualTo(MEADOW);
    assertThat(read(result, "$.items[0].title")).isEqualTo("How Honeybees Choose a New Home");
    assertThat(read(result, "$.items[0].savesCount")).isEqualTo(4);
    assertThat(read(result, "$.items[0].highlightsCount")).isEqualTo(3);
    assertThat(read(result, "$.items[0].clipsCount")).isEqualTo(1);
    assertThat(read(result, "$.items[0].importsCount")).isEqualTo(0);
    // Most used first.
    assertThat(JsonPath.<List<String>>read(body(result), "$.items[0].colors"))
        .containsExactly("GREEN", "YELLOW");
    // The clip came later but has no color.
    assertThat(read(result, "$.items[0].latestHighlight.text")).isEqualTo("green again");
    assertThat(read(result, "$.items[0].latestHighlight.color")).isEqualTo("GREEN");
    assertThat(Instant.parse(text(result, "$.items[0].firstSavedAt"))).isEqualTo(first);
    assertThat(Instant.parse(text(result, "$.items[0].lastSavedAt")))
        .isEqualTo(first.plusSeconds(4));
    assertThat(read(result, "$.items[1].savesCount")).isEqualTo(1);
    assertThat(JsonPath.<List<String>>read(body(result), "$.items[1].colors"))
        .containsExactly("PINK");
    assertThat(body(result)).doesNotContain("nextCursor");
  }

  @Test
  void aSourceWithOnlyClipsHasColorsButNoLatestHighlight() {
    Account account = signUp();
    create(account, tableClip(HIVE));

    MvcTestResult result = list(account, "");

    assertThat(read(result, "$.items[0].clipsCount")).isEqualTo(1);
    assertThat(JsonPath.<List<String>>read(body(result), "$.items[0].colors")).isEmpty();
    assertThat(body(result)).doesNotContain("latestHighlight", "author");
  }

  @Test
  void listsTheSourcesAPageAtATime() {
    Account account = signUp();
    for (int i = 1; i <= 3; i++) {
      create(account, highlight("t", "BLUE", "https://site.example/" + i, "Site " + i));
      clock.advance(Duration.ofSeconds(1));
    }

    MvcTestResult first = list(account, "limit=2");
    assertThat(names(first)).containsExactly("Site 3", "Site 2");
    MvcTestResult last = list(account, "limit=2&cursor=" + text(first, "$.nextCursor"));
    assertThat(names(last)).containsExactly("Site 1");
    assertThat(body(last)).doesNotContain("nextCursor");
  }

  @Test
  void sourcesSavedFromAtTheSameMomentAreNeitherSkippedNorRepeated() {
    Account account = signUp();
    // The test clock stands still, so only the ids tell these apart.
    for (int i = 1; i <= 5; i++) {
      create(account, highlight("t", "BLUE", "https://same.example/" + i, "Same " + i));
    }

    List<String> seen = new ArrayList<>();
    String cursor = null;
    do {
      MvcTestResult page = list(account, "limit=2" + (cursor == null ? "" : "&cursor=" + cursor));
      seen.addAll(names(page));
      cursor = body(page).contains("nextCursor") ? text(page, "$.nextCursor") : null;
    } while (cursor != null);

    assertThat(seen).hasSize(5).doesNotHaveDuplicates();
  }

  @Test
  void refusesACursorItDidNotMakeAndALimitOutOfRange() {
    Account account = signUp();

    MvcTestResult result = list(account, "cursor=garbage");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(read(result, "$.code")).isEqualTo("INVALID_CURSOR");
    assertThat(list(account, "limit=0")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(list(account, "limit=101")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(list(account, "kind=NOPE")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void filtersByKindAndCountsEachKind() {
    Account account = signUp();
    create(account, highlight("a", "BLUE", MEADOW, "Meadow Quarterly"));
    create(account, highlight("b", "BLUE", HIVE, "Hive Notes"));
    UUID file = fileSource(account, "swarm-notes.pdf", "Swarm Notes");
    importedMark(account, file, "marked in the PDF", "PURPLE", 4);

    MvcTestResult files = list(account, "kind=FILE");
    assertThat(names(files)).containsExactly("swarm-notes.pdf");
    assertThat(read(files, "$.items[0].kind")).isEqualTo("FILE");
    assertThat(read(files, "$.items[0].importsCount")).isEqualTo(1);
    assertThat(body(files)).doesNotContain("\"url\"");
    assertThat(names(list(account, "kind=WEBSITE")))
        .containsExactlyInAnyOrder("Meadow Quarterly", "Hive Notes");

    MvcTestResult counts = counts(account);
    assertThat(counts).hasStatusOk();
    assertThat(read(counts, "$.total")).isEqualTo(3);
    assertThat(JsonPath.<List<Object>>read(body(counts), "$.kinds[?(@.kind=='WEBSITE')].count"))
        .containsExactly(2);
    assertThat(JsonPath.<List<Object>>read(body(counts), "$.kinds[?(@.kind=='FILE')].count"))
        .containsExactly(1);
  }

  @Test
  void aSourceWithNothingSavedFromItIsLeftOut() {
    Account account = signUp();
    create(account, highlight("stays", "BLUE", MEADOW, "Meadow Quarterly"));
    MvcTestResult gone = create(account, highlight("goes", "BLUE", HIVE, "Hive Notes"));
    String hiveId = text(gone, "$.source.id");

    assertThat(delete(account, text(gone, "$.id"))).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(names(list(account, ""))).containsExactly("Meadow Quarterly");
    assertThat(read(counts(account), "$.total")).isEqualTo(1);
    assertThat(get(account, hiveId)).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void searchFindsSourcesByTheirNameTitleOrAuthor() {
    Account account = signUp();
    create(account, highlight("a", "BLUE", MEADOW, "Meadow Quarterly"));
    create(
        account,
        """
        {"kind": "HIGHLIGHT", "color": "PINK", "text": "q",
         "source": {"url": "%s", "name": "Hive Notes", "title": "Queens and Drones",
                    "author": "R. Okafor"}}
        """
            .formatted(HIVE));

    assertThat(names(list(account, "q=QUARTER"))).containsExactly("Meadow Quarterly");
    assertThat(names(list(account, "q=honeybees"))).containsExactly("Meadow Quarterly");
    assertThat(names(list(account, "q=okaf"))).containsExactly("Hive Notes");
    assertThat(names(list(account, "q=drones&kind=WEBSITE"))).containsExactly("Hive Notes");
    assertThat(names(list(account, "q=drones&kind=FILE"))).isEmpty();
    assertThat(names(list(account, "q=nothing"))).isEmpty();
  }

  // — One source —

  @Test
  void findsOneSourceWithWhatIsSavedFromIt() {
    Account account = signUp();
    String id =
        text(
            create(account, highlight("one", "YELLOW", MEADOW, "Meadow Quarterly")), "$.source.id");
    create(account, tableClip(MEADOW));
    create(account, highlight("elsewhere", "BLUE", HIVE, "Hive Notes"));

    MvcTestResult result = get(account, id);

    assertThat(result).hasStatusOk();
    assertThat(read(result, "$.id")).isEqualTo(id);
    assertThat(read(result, "$.name")).isEqualTo("Meadow Quarterly");
    assertThat(read(result, "$.savesCount")).isEqualTo(2);
    assertThat(read(result, "$.highlightsCount")).isEqualTo(1);
    assertThat(read(result, "$.clipsCount")).isEqualTo(1);
    assertThat(JsonPath.<List<String>>read(body(result), "$.colors")).containsExactly("YELLOW");
    assertThat(read(result, "$.latestHighlight.text")).isEqualTo("one");
    assertThat(get(account, UUID.randomUUID().toString())).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(get(account, "not-an-id")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void listsTheNotesQuotingEachSourceTheOneChangedMostRecentlyFirst() {
    Account account = signUp();
    String one =
        text(create(account, highlight("one", "BLUE", MEADOW, "Meadow Quarterly")), "$.id");
    String two =
        text(create(account, highlight("two", "BLUE", MEADOW, "Meadow Quarterly")), "$.id");
    MvcTestResult hive = create(account, highlight("queens", "PINK", HIVE, "Hive Notes"));
    String draft = note(account, "Essay draft", one, two);
    clock.advance(Duration.ofSeconds(1));
    String checklist = note(account, "", one, text(hive, "$.id"));

    MvcTestResult result = list(account, "");

    assertThat(
            JsonPath.<List<String>>read(
                body(result), "$.items[?(@.url=='" + MEADOW + "')].notes[*].id"))
        .containsExactly(checklist, draft);
    assertThat(
            JsonPath.<List<Object>>read(
                body(result), "$.items[?(@.url=='" + MEADOW + "')].notes[*].quotesCount"))
        .containsExactly(1, 2);
    assertThat(
            JsonPath.<List<String>>read(
                body(result), "$.items[?(@.url=='" + MEADOW + "')].notes[*].title"))
        .containsExactly("", "Essay draft");
    assertThat(
            JsonPath.<List<String>>read(
                body(get(account, text(hive, "$.source.id"))), "$.notes[*].id"))
        .containsExactly(checklist);
  }

  // — Whose sources —

  @Test
  void aSourceIsOnlyEverTheSignedInUsers() {
    Account ada = signUp();
    Account grace = signUp();
    String adas =
        text(create(ada, highlight("ada's", "BLUE", MEADOW, "Meadow Quarterly")), "$.source.id");

    assertThat(get(grace, adas)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(names(list(grace, ""))).isEmpty();
    assertThat(read(counts(grace), "$.total")).isEqualTo(0);
    assertThat(names(list(ada, ""))).containsExactly("Meadow Quarterly");
  }

  @Test
  void needsASignedInUser() {
    assertThat(mvc.get().uri("/api/v1/sources").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/api/v1/sources/counts").exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/api/v1/sources/" + UUID.randomUUID()).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void theOpenApiDescriptionShowsTheSourcesEndpoints() {
    MvcTestResult docs = mvc.get().uri("/v3/api-docs").exchange();

    assertThat(docs).hasStatusOk();
    String json = body(docs);
    assertThat(
            JsonPath.<List<String>>read(json, "$.paths['/api/v1/sources'].get.parameters[*].name"))
        .containsExactly("kind", "q", "cursor", "limit");
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/sources/counts'].get")).isNotNull();
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/sources/{id}'].get")).isNotNull();
    assertThat(
            JsonPath.<List<String>>read(
                json, "$.components.schemas.SourceSummaryResponse.properties.colors.items.enum"))
        .containsExactly("YELLOW", "GREEN", "BLUE", "PINK", "PURPLE");
  }

  // — Helpers —

  private record Account(UUID id, String accessToken) {}

  private Account signUp() {
    String username = "src_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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

  private static String highlight(String text, String color, String url, String name) {
    return """
        {"kind": "HIGHLIGHT", "color": "%s", "text": "%s",
         "source": {"url": "%s", "name": "%s", "title": "How Honeybees Choose a New Home"}}
        """
        .formatted(color, text, url, name);
  }

  private static String tableClip(String url) {
    return """
        {"kind": "CLIP", "clipType": "TABLE", "text": "Scouts by season", "contentMd": "| a |",
         "source": {"url": "%s", "name": "Meadow Quarterly",
                    "title": "How Honeybees Choose a New Home"}}
        """
        .formatted(url);
  }

  /** An imported file's source; ari-api can't import files yet, so it goes straight in. */
  private UUID fileSource(Account account, String name, String title) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO sources (id, user_id, kind, name, title) VALUES (?, ?, 'FILE', ?, ?)",
        id,
        account.id(),
        name,
        title);
    return id;
  }

  private void importedMark(Account account, UUID sourceId, String text, String color, int page) {
    jdbc.update(
        "INSERT INTO saves (id, user_id, source_id, kind, color, text, page_number, created_at)"
            + " VALUES (?, ?, ?, 'IMPORT', ?, ?, ?, ?)",
        UUID.randomUUID(),
        account.id(),
        sourceId,
        color,
        text,
        page,
        Timestamp.from(clock.instant()));
  }

  private MvcTestResult create(Account account, String json) {
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/saves")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
            .cookie(new Cookie(ACCESS, account.accessToken()))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return result;
  }

  /** A note quoting these saves; returns its id. */
  private String note(Account account, String title, String... saveIds) {
    String blocks =
        Arrays.stream(saveIds)
            .map(id -> "{\"kind\": \"QUOTE\", \"saveId\": \"" + id + "\"}")
            .collect(Collectors.joining(", "));
    MvcTestResult result =
        mvc.post()
            .uri("/api/v1/notes")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"title\": \"%s\", \"blocks\": [%s]}".formatted(title, blocks))
            .cookie(new Cookie(ACCESS, account.accessToken()))
            .exchange();
    assertThat(result).hasStatus(HttpStatus.CREATED);
    return text(result, "$.id");
  }

  private MvcTestResult delete(Account account, String saveId) {
    return mvc.delete()
        .uri("/api/v1/saves/" + saveId)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult list(Account account, String query) {
    return mvc.get()
        .uri("/api/v1/sources?" + query)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult counts(Account account) {
    return mvc.get()
        .uri("/api/v1/sources/counts")
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult get(Account account, String id) {
    return mvc.get()
        .uri("/api/v1/sources/" + id)
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

  private static List<String> names(MvcTestResult page) {
    assertThat(page).hasStatusOk();
    return JsonPath.read(body(page), "$.items[*].name");
  }
}
