package app.ari.api.feature.save.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import app.ari.api.support.MutableClock;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** The Library's endpoints on real PostgreSQL and MinIO. */
@IntegrationTest
class SaveControllerTest {

  private static final String BUCKET = "ari";
  private static final String PAGE = "https://meadow.example/honeybees";

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private S3Client s3;
  @Autowired private MutableClock clock;

  // — Saving —

  @Test
  void savesAHighlightWithItsSourceAndTags() {
    Account account = signUp();

    MvcTestResult result =
        create(
            account,
            """
            {"kind": "HIGHLIGHT", "color": "YELLOW", "text": "  In late spring a colony splits.  ",
             "comment": "Opening line", "section": "Why swarms split", "tags": ["Swarm", "bees"],
             "source": {"url": "HTTPS://Meadow.Example/honeybees?id=7#:~:text=In%20late",
                        "name": "Meadow Quarterly", "title": "How Honeybees Choose a New Home",
                        "author": "J. Arden"}}
            """);

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(read(result, "$.id")).isNotNull();
    assertThat(read(result, "$.kind")).isEqualTo("HIGHLIGHT");
    assertThat(read(result, "$.color")).isEqualTo("YELLOW");
    assertThat(read(result, "$.text")).isEqualTo("In late spring a colony splits.");
    assertThat(read(result, "$.comment")).isEqualTo("Opening line");
    assertThat(read(result, "$.section")).isEqualTo("Why swarms split");
    assertThat(JsonPath.<List<String>>read(body(result), "$.tags"))
        .containsExactly("bees", "Swarm");
    assertThat(read(result, "$.source.kind")).isEqualTo("WEBSITE");
    assertThat(read(result, "$.source.url")).isEqualTo("https://meadow.example/honeybees?id=7");
    assertThat(read(result, "$.source.name")).isEqualTo("Meadow Quarterly");
    assertThat(read(result, "$.source.title")).isEqualTo("How Honeybees Choose a New Home");
    assertThat(read(result, "$.source.author")).isEqualTo("J. Arden");
    assertThat(read(result, "$.createdAt")).isNotNull();
    assertThat(body(result)).doesNotContain("clipType", "contentMd", "imageUrl", "pageNumber");
    assertThat(savesCount(account)).isEqualTo(1);
  }

  @Test
  void savingFromTheSamePageAgainReusesItsSource() {
    Account account = signUp();

    String first =
        text(create(account, highlight("one", "GREEN", "[]", PAGE + "#a")), "$.source.id");
    MvcTestResult second =
        create(
            account,
            """
            {"kind": "HIGHLIGHT", "color": "PINK", "text": "two",
             "source": {"url": "https://MEADOW.example/honeybees#b", "name": "Meadow Q.",
                        "title": "A newer title"}}
            """);

    assertThat(read(second, "$.source.id")).isEqualTo(first);
    assertThat(read(second, "$.source.title")).isEqualTo("A newer title");
    assertThat(body(second)).doesNotContain("author");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sources WHERE user_id = ?", Integer.class, account.id()))
        .isEqualTo(1);
  }

  @Test
  void aLaterSaveWithoutAnAuthorKeepsTheKnownOne() {
    Account account = signUp();
    create(
        account,
        """
        {"kind": "HIGHLIGHT", "color": "BLUE", "text": "a",
         "source": {"url": "https://author.example/p", "name": "S", "title": "T",
                    "author": "R. Okafor"}}
        """);

    MvcTestResult second =
        create(account, highlight("b", "BLUE", "[]", "https://author.example/p"));

    assertThat(read(second, "$.source.author")).isEqualTo("R. Okafor");
  }

  @Test
  void differentUsersNeverShareASource() {
    Account ada = signUp();
    Account grace = signUp();

    String adas = text(create(ada, highlight("a", "BLUE", "[]", PAGE)), "$.source.id");
    String graces = text(create(grace, highlight("g", "BLUE", "[]", PAGE)), "$.source.id");

    assertThat(adas).isNotEqualTo(graces);
  }

  @Test
  void theSameTagInAnyCaseIsOneTagAndKeepsItsFirstSpelling() {
    Account account = signUp();

    MvcTestResult first = create(account, highlight("a", "BLUE", "[\"Swarm\", \"swarm\"]", PAGE));
    MvcTestResult second = create(account, highlight("b", "BLUE", "[\"SWARM\", \"queen\"]", PAGE));

    assertThat(JsonPath.<List<String>>read(body(first), "$.tags")).containsExactly("Swarm");
    assertThat(JsonPath.<List<String>>read(body(second), "$.tags"))
        .containsExactly("queen", "Swarm");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM tags WHERE user_id = ?", Integer.class, account.id()))
        .isEqualTo(2);
  }

  @Test
  void savesATableClipWithItsMarkdown() {
    Account account = signUp();

    MvcTestResult result =
        create(
            account,
            """
            {"kind": "CLIP", "clipType": "TABLE", "text": "Scouts by season",
             "contentMd": "| season | scouts |\\n|---|---|\\n| spring | 40 |",
             "source": {"url": "https://meadow.example/table", "name": "Meadow", "title": "T"}}
            """);

    assertThat(result).hasStatus(HttpStatus.CREATED);
    assertThat(read(result, "$.kind")).isEqualTo("CLIP");
    assertThat(read(result, "$.clipType")).isEqualTo("TABLE");
    assertThat(read(result, "$.contentMd")).asString().startsWith("| season | scouts |");
    assertThat(body(result)).doesNotContain("\"color\"", "imageUrl");
  }

  @Test
  void savesAnImageClipInStorageAndShowsItThroughASignedLink() throws Exception {
    Account account = signUp();
    byte[] picture = image("png");

    MvcTestResult result = createWithImage(account, imageClipJson(), picture, "image/png");

    assertThat(result).hasStatus(HttpStatus.CREATED);
    String id = text(result, "$.id");
    assertThat(read(result, "$.clipType")).isEqualTo("IMAGE");
    assertThat(imageKey(id)).isEqualTo("saves/" + account.id() + "/" + id + ".png");
    HttpResponse<byte[]> shown = get(text(result, "$.imageUrl"));
    assertThat(shown.statusCode()).isEqualTo(200);
    assertThat(shown.body()).isEqualTo(picture);
    assertThat(shown.headers().firstValue("Content-Type")).contains("image/png");
    assertThat(savesCount(account)).isEqualTo(1);
  }

  @Test
  void deletingAnImageClipDeletesItsFile() {
    Account account = signUp();
    String id = text(createWithImage(account, imageClipJson(), image("jpg"), "image/jpeg"), "$.id");
    String key = imageKey(id);
    assertThat(exists(key)).isTrue();

    assertThat(delete(account, id)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(exists(key)).isFalse();
  }

  @Test
  void aSaveThatFailsLeavesNoImageBehind() {
    Account account = signUp();

    // The shape check fails after the image was read, so nothing may be stored.
    MvcTestResult result =
        createWithImage(
            account,
            """
            {"kind": "HIGHLIGHT", "color": "BLUE", "text": "t",
             "source": {"url": "https://meadow.example/p", "name": "M", "title": "T"}}
            """,
            image("png"),
            "image/png");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(storedKeys(account)).isEmpty();
    assertThat(savesCount(account)).isZero();
  }

  // — Rules —

  @Test
  void refusesASaveThatBreaksItsKindsRules() {
    Account account = signUp();
    String source = "\"source\": {\"url\": \"" + PAGE + "\", \"name\": \"M\", \"title\": \"T\"}";

    assertInvalidSave(account, "{\"kind\": \"HIGHLIGHT\", \"text\": \"t\", " + source + "}");
    assertInvalidSave(
        account,
        "{\"kind\": \"CLIP\", \"clipType\": \"TABLE\", \"color\": \"BLUE\", \"text\": \"t\","
            + " \"contentMd\": \"| a |\", "
            + source
            + "}");
    assertInvalidSave(account, "{\"kind\": \"CLIP\", \"text\": \"t\", " + source + "}");
    assertInvalidSave(
        account, "{\"kind\": \"CLIP\", \"clipType\": \"TABLE\", \"text\": \"t\", " + source + "}");
    // An image clip without its image part.
    assertInvalidSave(
        account, "{\"kind\": \"CLIP\", \"clipType\": \"IMAGE\", \"text\": \"t\", " + source + "}");
    assertInvalidSave(
        account, "{\"kind\": \"IMPORT\", \"color\": \"BLUE\", \"text\": \"t\", " + source + "}");
    assertThat(savesCount(account)).isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sources WHERE user_id = ?", Integer.class, account.id()))
        .isZero();
  }

  @Test
  void refusesAnInvalidFieldWithItsName() {
    Account account = signUp();

    MvcTestResult noText =
        create(
            account,
            """
            {"kind": "HIGHLIGHT", "color": "BLUE", "text": "  ",
             "source": {"url": "https://meadow.example/p", "name": "M", "title": "T"}}
            """);
    MvcTestResult noSource =
        create(account, "{\"kind\": \"HIGHLIGHT\", \"color\": \"BLUE\", \"text\": \"t\"}");
    MvcTestResult tooManyTags =
        create(
            account,
            highlight(
                "t",
                "BLUE",
                "[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\",\"10\",\"11\"]",
                PAGE));
    MvcTestResult unknownColor =
        create(
            account,
            """
            {"kind": "HIGHLIGHT", "color": "TEAL", "text": "t",
             "source": {"url": "https://meadow.example/p", "name": "M", "title": "T"}}
            """);

    assertThat(noText).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(read(noText, "$.code")).isEqualTo("VALIDATION_FAILED");
    assertThat(read(noText, "$.errors[0].field")).isEqualTo("text");
    assertThat(read(noSource, "$.code")).isEqualTo("VALIDATION_FAILED");
    assertThat(read(noSource, "$.errors[0].field")).isEqualTo("source");
    assertThat(read(tooManyTags, "$.code")).isEqualTo("VALIDATION_FAILED");
    assertThat(unknownColor).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(savesCount(account)).isZero();
  }

  @Test
  void onlyHttpAndHttpsPagesAreSources() {
    Account account = signUp();

    MvcTestResult result = create(account, highlight("t", "BLUE", "[]", "ftp://meadow.example/p"));

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(read(result, "$.code")).isEqualTo("INVALID_URL");
    assertThat(savesCount(account)).isZero();
  }

  @Test
  void onlyRealImagesAreAcceptedWhateverTheUploadClaims() {
    Account account = signUp();

    MvcTestResult result =
        createWithImage(
            account,
            imageClipJson(),
            "<svg onload=alert(1)/>".getBytes(StandardCharsets.UTF_8),
            "image/png");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(read(result, "$.code")).isEqualTo("INVALID_IMAGE");
    assertThat(savesCount(account)).isZero();
  }

  @Test
  void anImageOverTwoMegabytesIsRefused() {
    Account account = signUp();
    byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
    System.arraycopy(
        new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0, tooBig, 0, 8);

    // The servlet takes up to 50 MB, for imports; the clip's own limit is checked in the facade.
    MvcTestResult result = createWithImage(account, imageClipJson(), tooBig, "image/png");

    assertThat(result).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(read(result, "$.code")).isEqualTo("FILE_TOO_LARGE");
    assertThat(savesCount(account)).isZero();
  }

  // — Reading —

  @Test
  void listsTheNewestFirstAPageAtATime() {
    Account account = signUp();
    for (int i = 1; i <= 5; i++) {
      create(account, highlight("save " + i, "BLUE", "[]", PAGE));
      clock.advance(Duration.ofSeconds(1));
    }

    MvcTestResult first = list(account, "limit=2");
    assertThat(texts(first)).containsExactly("save 5", "save 4");
    String cursor = text(first, "$.nextCursor");
    MvcTestResult second = list(account, "limit=2&cursor=" + cursor);
    assertThat(texts(second)).containsExactly("save 3", "save 2");
    MvcTestResult last = list(account, "limit=2&cursor=" + text(second, "$.nextCursor"));
    assertThat(texts(last)).containsExactly("save 1");
    assertThat(body(last)).doesNotContain("nextCursor");
  }

  @Test
  void savesMadeAtTheSameMomentAreNeitherSkippedNorRepeated() {
    Account account = signUp();
    // The test clock stands still, so all of these have one created_at; only the id tells them
    // apart.
    for (int i = 1; i <= 5; i++) {
      create(account, highlight("same moment " + i, "BLUE", "[]", PAGE));
    }

    List<String> seen = new ArrayList<>();
    String cursor = null;
    do {
      MvcTestResult page = list(account, "limit=2" + (cursor == null ? "" : "&cursor=" + cursor));
      seen.addAll(texts(page));
      cursor = body(page).contains("nextCursor") ? text(page, "$.nextCursor") : null;
    } while (cursor != null);

    assertThat(seen).hasSize(5).doesNotHaveDuplicates();
  }

  @Test
  void theFirstPageIsFortySaves() {
    Account account = signUp();
    for (int i = 0; i < 41; i++) {
      create(account, highlight("bulk " + i, "BLUE", "[]", PAGE));
    }

    assertThat(texts(list(account, ""))).hasSize(40);
  }

  @Test
  void listsAWebPagesSavesInTheOrderTheyWereSaved() {
    Account account = signUp();
    String source = text(create(account, highlight("first", "BLUE", "[]", PAGE)), "$.source.id");
    clock.advance(Duration.ofSeconds(1));
    create(account, highlight("second", "BLUE", "[]", PAGE));
    clock.advance(Duration.ofSeconds(1));
    create(account, highlight("elsewhere", "BLUE", "[]", "https://hive.example/queens"));
    create(account, highlight("third", "BLUE", "[]", PAGE));

    assertThat(texts(list(account, "source=" + source + "&order=PAGE")))
        .containsExactly("first", "second", "third");
    assertThat(texts(list(account, "source=" + source)))
        .containsExactly("third", "second", "first");
  }

  @Test
  void listsAFilesSavesByPageAPageAtATime() {
    Account account = signUp();
    UUID file = fileSource(account);
    importedMark(account, file, "page 3", 3);
    importedMark(account, file, "page 1, saved first", 1);
    clock.advance(Duration.ofSeconds(1));
    importedMark(account, file, "page 2", 2);
    importedMark(account, file, "page 1, saved later", 1);

    List<String> seen = new ArrayList<>();
    String cursor = null;
    do {
      MvcTestResult page =
          list(
              account,
              "source="
                  + file
                  + "&order=PAGE&limit=1"
                  + (cursor == null ? "" : "&cursor=" + cursor));
      seen.addAll(texts(page));
      cursor = body(page).contains("nextCursor") ? text(page, "$.nextCursor") : null;
    } while (cursor != null);

    assertThat(seen)
        .containsExactly("page 1, saved first", "page 1, saved later", "page 2", "page 3");
  }

  @Test
  void aCursorGoesOnOnlyInTheOrderItWasMadeFor() {
    Account account = signUp();
    create(account, highlight("a", "BLUE", "[]", PAGE));
    create(account, highlight("b", "BLUE", "[]", PAGE));
    String newest = text(list(account, "limit=1"), "$.nextCursor");
    String inPageOrder = text(list(account, "order=PAGE&limit=1"), "$.nextCursor");

    MvcTestResult mixed = list(account, "order=PAGE&cursor=" + newest);

    assertThat(mixed).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(read(mixed, "$.code")).isEqualTo("INVALID_CURSOR");
    assertThat(list(account, "cursor=" + inPageOrder)).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(list(account, "order=NOPE")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void refusesACursorItDidNotMake() {
    Account account = signUp();

    MvcTestResult result = list(account, "cursor=garbage");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(read(result, "$.code")).isEqualTo("INVALID_CURSOR");
    assertThat(list(account, "limit=0")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(list(account, "limit=101")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(list(account, "type=NOPE")).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void filtersByTypeTagColorAndSource() {
    Account account = signUp();
    String meadow = "https://meadow.example/a";
    String hive = "https://hive.example/b";
    String meadowId =
        text(create(account, highlight("y-swarm", "YELLOW", "[\"Swarm\"]", meadow)), "$.source.id");
    create(account, highlight("g-swarm", "GREEN", "[\"swarm\", \"bees\"]", meadow));
    create(account, highlight("g-queen", "GREEN", "[\"queen\"]", hive));
    create(
        account,
        """
        {"kind": "CLIP", "clipType": "TABLE", "text": "table", "contentMd": "| a |",
         "tags": ["swarm"], "source": {"url": "https://hive.example/b", "name": "H", "title": "T"}}
        """);

    assertThat(texts(list(account, "tag=SWARM")))
        .containsExactlyInAnyOrder("y-swarm", "g-swarm", "table");
    assertThat(texts(list(account, "tag=queen&tag=bees")))
        .containsExactlyInAnyOrder("g-queen", "g-swarm");
    assertThat(texts(list(account, "color=GREEN"))).containsExactlyInAnyOrder("g-swarm", "g-queen");
    assertThat(texts(list(account, "color=GREEN&color=YELLOW"))).hasSize(3);
    assertThat(texts(list(account, "type=CLIP"))).containsExactly("table");
    assertThat(texts(list(account, "source=" + meadowId)))
        .containsExactlyInAnyOrder("y-swarm", "g-swarm");
    // Each group that is given must match.
    assertThat(texts(list(account, "source=" + meadowId + "&color=GREEN&tag=swarm")))
        .containsExactly("g-swarm");
    assertThat(texts(list(account, "type=HIGHLIGHT&tag=swarm&color=YELLOW")))
        .containsExactly("y-swarm");
    assertThat(texts(list(account, "tag=nothing"))).isEmpty();
  }

  @Test
  void facetsCountTheTypeTabsByTheFiltersAndTheRestOverTheWholeLibrary() {
    Account account = signUp();
    String meadowId =
        text(
            create(account, highlight("a", "YELLOW", "[\"swarm\"]", "https://meadow.example/a")),
            "$.source.id");
    create(account, highlight("b", "GREEN", "[\"swarm\", \"bees\"]", "https://meadow.example/a"));
    create(account, highlight("c", "GREEN", "[\"queen\"]", "https://hive.example/b"));
    create(
        account,
        """
        {"kind": "CLIP", "clipType": "TABLE", "text": "t", "contentMd": "| a |",
         "tags": ["swarm"], "source": {"url": "https://hive.example/b", "name": "Hive", "title": "T"}}
        """);

    MvcTestResult all = facets(account, "");
    assertThat(read(all, "$.total")).isEqualTo(4);
    assertThat(JsonPath.<List<Object>>read(body(all), "$.types[?(@.kind=='HIGHLIGHT')].count"))
        .containsExactly(3);
    assertThat(JsonPath.<List<Object>>read(body(all), "$.types[?(@.kind=='CLIP')].count"))
        .containsExactly(1);
    assertThat(JsonPath.<List<Object>>read(body(all), "$.types[?(@.kind=='IMPORT')].count"))
        .containsExactly(0);
    assertThat(JsonPath.<List<String>>read(body(all), "$.tags[*].name"))
        .containsExactly("swarm", "bees", "queen");
    assertThat(JsonPath.<List<Object>>read(body(all), "$.tags[*].count")).containsExactly(3, 1, 1);
    assertThat(JsonPath.<List<String>>read(body(all), "$.colors[*].color"))
        .containsExactly("YELLOW", "GREEN", "BLUE", "PINK", "PURPLE");
    assertThat(JsonPath.<List<Object>>read(body(all), "$.colors[*].count"))
        .containsExactly(1, 2, 0, 0, 0);
    assertThat(JsonPath.<List<String>>read(body(all), "$.sources[*].name"))
        .containsExactlyInAnyOrder("Meadow Quarterly", "Hive");
    assertThat(JsonPath.<List<Object>>read(body(all), "$.sources[*].count")).containsExactly(2, 2);

    // A tag filter narrows the type tabs but not the choices themselves.
    MvcTestResult swarm = facets(account, "tag=swarm");
    assertThat(read(swarm, "$.total")).isEqualTo(3);
    assertThat(JsonPath.<List<Object>>read(body(swarm), "$.types[?(@.kind=='HIGHLIGHT')].count"))
        .containsExactly(2);
    assertThat(JsonPath.<List<Object>>read(body(swarm), "$.types[?(@.kind=='CLIP')].count"))
        .containsExactly(1);
    assertThat(JsonPath.<List<String>>read(body(swarm), "$.tags[*].name"))
        .containsExactly("swarm", "bees", "queen");
    assertThat(
            JsonPath.<List<String>>read(
                body(facets(account, "source=" + meadowId)), "$.sources[*].id"))
        .hasSize(2);
  }

  // — Search —

  @Test
  void searchFindsTheStartsOfWordsInTheTextCommentAndSectionInAnyCaseOrAccent() {
    Account account = signUp();
    create(
        account,
        """
        {"kind": "HIGHLIGHT", "color": "BLUE", "text": "Scouts dance for the best nest site",
         "comment": "Seeley's Ölçü", "section": "Quorum sensing",
         "source": {"url": "https://meadow.example/a", "name": "Meadow", "title": "T"}}
        """);
    create(account, highlight("Something else entirely", "BLUE", "[]", "https://other.example/b"));

    assertThat(texts(list(account, "q=NEST+sco")))
        .containsExactly("Scouts dance for the best nest site");
    assertThat(texts(list(account, "q=olcu")))
        .containsExactly("Scouts dance for the best nest site");
    assertThat(texts(list(account, "q=quor")))
        .containsExactly("Scouts dance for the best nest site");
    assertThat(texts(list(account, "q=nest+else"))).isEmpty();
  }

  @Test
  void searchFindsPartOfTheTextAndTheSavesOfAMatchingSource() {
    Account account = signUp();
    create(account, highlight("Honeybees choose", "BLUE", "[]", "https://meadow.example/a"));
    create(
        account,
        """
        {"kind": "HIGHLIGHT", "color": "BLUE", "text": "Queens fly out",
         "source": {"url": "https://hive.example/q", "name": "Hive Notes",
                    "title": "Mating Flights", "author": "R. Okafor"}}
        """);

    assertThat(texts(list(account, "q=bees"))).containsExactly("Honeybees choose");
    assertThat(texts(list(account, "q=mating"))).containsExactly("Queens fly out");
    assertThat(texts(list(account, "q=okafor"))).containsExactly("Queens fly out");
    // LIKE's own characters are only themselves.
    assertThat(texts(search(account, "bee%", ""))).isEmpty();
  }

  @Test
  void searchGoesWithTheFiltersPagesAndCounts() {
    Account account = signUp();
    for (int i = 1; i <= 3; i++) {
      create(account, highlight("swarm " + i, i == 2 ? "PINK" : "BLUE", "[]", PAGE));
      clock.advance(Duration.ofSeconds(1));
    }
    create(account, highlight("unrelated", "PINK", "[]", PAGE));

    MvcTestResult first = list(account, "q=swarm&limit=2");
    assertThat(texts(first)).containsExactly("swarm 3", "swarm 2");
    assertThat(texts(list(account, "q=swarm&limit=2&cursor=" + text(first, "$.nextCursor"))))
        .containsExactly("swarm 1");
    assertThat(texts(list(account, "q=swarm&color=PINK"))).containsExactly("swarm 2");
    MvcTestResult counts = facets(account, "q=swarm");
    assertThat(read(counts, "$.total")).isEqualTo(3);
    // The choices still count the whole Library.
    assertThat(JsonPath.<List<Object>>read(body(counts), "$.colors[?(@.color=='PINK')].count"))
        .containsExactly(2);
    // Nothing to search for is no search.
    assertThat(texts(search(account, "&!", ""))).hasSize(4);
    assertThat(list(account, "q=" + "x".repeat(201))).hasStatus(HttpStatus.BAD_REQUEST);
  }

  @Test
  void searchOnlyEverFindsTheSignedInUsersSaves() {
    Account ada = signUp();
    Account grace = signUp();
    create(ada, highlight("private swarm", "BLUE", "[]", PAGE));

    assertThat(texts(list(grace, "q=swarm"))).isEmpty();
    assertThat(texts(list(grace, "q=meadow"))).isEmpty();
  }

  // — One save —

  @Test
  void findsOneSaveWithItsSourceAndTags() {
    Account account = signUp();
    String id = text(create(account, highlight("t", "YELLOW", "[\"b\", \"a\"]", PAGE)), "$.id");
    create(account, highlight("other", "BLUE", "[]", PAGE));

    MvcTestResult result = get(account, id);

    assertThat(result).hasStatusOk();
    assertThat(read(result, "$.id")).isEqualTo(id);
    assertThat(read(result, "$.text")).isEqualTo("t");
    assertThat(read(result, "$.color")).isEqualTo("YELLOW");
    assertThat(JsonPath.<List<String>>read(body(result), "$.tags")).containsExactly("a", "b");
    assertThat(read(result, "$.source.url")).isEqualTo(PAGE);
    assertThat(read(result, "$.createdAt")).isNotNull();
    assertThat(get(account, UUID.randomUUID().toString())).hasStatus(HttpStatus.NOT_FOUND);
  }

  // — Changing and removing —

  @Test
  void changesTheColorCommentAndTags() {
    Account account = signUp();
    String id = text(create(account, highlight("t", "YELLOW", "[\"old\"]", PAGE)), "$.id");

    MvcTestResult result =
        update(
            account,
            id,
            "{\"color\": \"PURPLE\", \"comment\": \"Check this\", \"tags\": [\"New\", \"a\"]}");

    assertThat(result).hasStatusOk();
    assertThat(read(result, "$.color")).isEqualTo("PURPLE");
    assertThat(read(result, "$.comment")).isEqualTo("Check this");
    assertThat(JsonPath.<List<String>>read(body(result), "$.tags")).containsExactly("a", "New");
    assertThat(read(result, "$.source.url")).isEqualTo(PAGE);
    assertThat(texts(list(account, "tag=old"))).isEmpty();
    assertThat(texts(list(account, "tag=new"))).containsExactly("t");
  }

  @Test
  void aFieldLeftOutStaysAsItWas() {
    Account account = signUp();
    String id =
        text(
            create(
                account,
                """
                {"kind": "HIGHLIGHT", "color": "GREEN", "text": "t", "comment": "keep me",
                 "tags": ["a", "b"],
                 "source": {"url": "https://meadow.example/p", "name": "M", "title": "T"}}
                """),
            "$.id");

    MvcTestResult recolored = update(account, id, "{\"color\": \"PINK\"}");
    assertThat(read(recolored, "$.color")).isEqualTo("PINK");
    assertThat(read(recolored, "$.comment")).isEqualTo("keep me");
    assertThat(JsonPath.<List<String>>read(body(recolored), "$.tags")).containsExactly("a", "b");

    MvcTestResult cleared = update(account, id, "{\"comment\": \"\", \"tags\": []}");
    assertThat(read(cleared, "$.comment")).isEqualTo("");
    assertThat(JsonPath.<List<String>>read(body(cleared), "$.tags")).isEmpty();
    assertThat(read(cleared, "$.color")).isEqualTo("PINK");
    assertThat(update(account, id, "{}")).hasStatusOk();
  }

  @Test
  void aClipHasNoColorToChange() {
    Account account = signUp();
    String id =
        text(
            create(
                account,
                """
                {"kind": "CLIP", "clipType": "TABLE", "text": "t", "contentMd": "| a |",
                 "source": {"url": "https://meadow.example/p", "name": "M", "title": "T"}}
                """),
            "$.id");

    MvcTestResult result = update(account, id, "{\"color\": \"BLUE\"}");

    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(read(result, "$.code")).isEqualTo("INVALID_SAVE");
    assertThat(update(account, id, "{\"comment\": \"a note\"}")).hasStatusOk();
  }

  @Test
  void deletingASaveTakesItsCountAndTagLinksWithIt() {
    Account account = signUp();
    String id = text(create(account, highlight("t", "BLUE", "[\"a\", \"b\"]", PAGE)), "$.id");
    create(account, highlight("other", "BLUE", "[\"a\"]", PAGE));
    assertThat(savesCount(account)).isEqualTo(2);

    assertThat(delete(account, id)).hasStatus(HttpStatus.NO_CONTENT);

    assertThat(savesCount(account)).isEqualTo(1);
    assertThat(texts(list(account, ""))).containsExactly("other");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM save_tags WHERE save_id = ?",
                Integer.class,
                UUID.fromString(id)))
        .isZero();
    // A tag nothing uses any more is not offered.
    assertThat(JsonPath.<List<String>>read(body(facets(account, "")), "$.tags[*].name"))
        .containsExactly("a");
    assertThat(delete(account, id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(savesCount(account)).isEqualTo(1);
  }

  // — Whose saves —

  @Test
  void aSaveIsOnlyEverTheSignedInUsers() {
    Account ada = signUp();
    Account grace = signUp();
    String id = text(create(ada, highlight("ada's", "BLUE", "[\"private\"]", PAGE)), "$.id");
    create(grace, highlight("grace's", "BLUE", "[]", PAGE));

    assertThat(get(grace, id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(update(grace, id, "{\"comment\": \"mine now\"}")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(delete(grace, id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(texts(list(grace, ""))).containsExactly("grace's");
    assertThat(texts(list(ada, ""))).containsExactly("ada's");
    assertThat(JsonPath.<List<String>>read(body(facets(grace, "")), "$.tags[*].name")).isEmpty();
    assertThat(savesCount(ada)).isEqualTo(1);
    assertThat(read(update(ada, id, "{}"), "$.text")).isEqualTo("ada's");
  }

  @Test
  void needsASignedInUser() {
    assertThat(mvc.get().uri("/api/v1/saves").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/api/v1/saves/facets").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(
            mvc.post()
                .uri("/api/v1/saves")
                .contentType(MediaType.APPLICATION_JSON)
                .content(highlight("t", "BLUE", "[]", PAGE))
                .exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.get().uri("/api/v1/saves/" + UUID.randomUUID()).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
    assertThat(mvc.delete().uri("/api/v1/saves/" + UUID.randomUUID()).exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void theSidebarCountFollowsTheSaves() {
    Account account = signUp();
    create(account, highlight("a", "BLUE", "[]", PAGE));
    String id = text(create(account, highlight("b", "BLUE", "[]", PAGE)), "$.id");

    assertThat(
            mvc.get()
                .uri("/api/v1/me")
                .cookie(new Cookie(ACCESS, account.accessToken()))
                .exchange())
        .bodyJson()
        .extractingPath("$.savesCount")
        .isEqualTo(2);
    delete(account, id);
    assertThat(savesCount(account)).isEqualTo(1);
  }

  @Test
  void theOpenApiDescriptionShowsTheFiltersAndBothWaysToSave() {
    MvcTestResult docs = mvc.get().uri("/v3/api-docs").exchange();

    assertThat(docs).hasStatusOk();
    String json = body(docs);
    assertThat(JsonPath.<List<String>>read(json, "$.paths['/api/v1/saves'].get.parameters[*].name"))
        .containsExactly("type", "tag", "color", "source", "q", "order", "cursor", "limit");
    assertThat(
            JsonPath.<List<String>>read(
                json, "$.paths['/api/v1/saves/facets'].get.parameters[*].name"))
        .containsExactly("type", "tag", "color", "source", "q");
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/saves'].post.requestBody.content"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsOnlyKeys("application/json", "multipart/form-data");
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/saves/{id}'].get")).isNotNull();
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/saves/{id}'].patch")).isNotNull();
    assertThat(JsonPath.<Object>read(json, "$.paths['/api/v1/saves/{id}'].delete")).isNotNull();
  }

  // — Helpers —

  private record Account(UUID id, String accessToken) {}

  private Account signUp() {
    String username = "save_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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

  private static String highlight(String text, String color, String tags, String url) {
    return """
        {"kind": "HIGHLIGHT", "color": "%s", "text": "%s", "tags": %s,
         "source": {"url": "%s", "name": "Meadow Quarterly", "title": "How Honeybees Choose a New Home"}}
        """
        .formatted(color, text, tags, url);
  }

  /** An imported file's source; ari-api can't import files yet, so it goes straight in. */
  private UUID fileSource(Account account) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO sources (id, user_id, kind, name, title)"
            + " VALUES (?, ?, 'FILE', 'swarm-notes.pdf', 'Swarm Notes')",
        id,
        account.id());
    return id;
  }

  private void importedMark(Account account, UUID sourceId, String text, int page) {
    jdbc.update(
        "INSERT INTO saves (id, user_id, source_id, kind, color, text, page_number, created_at)"
            + " VALUES (?, ?, ?, 'IMPORT', 'YELLOW', ?, ?, ?)",
        UUID.randomUUID(),
        account.id(),
        sourceId,
        text,
        page,
        Timestamp.from(clock.instant()));
  }

  private static String imageClipJson() {
    return """
        {"kind": "CLIP", "clipType": "IMAGE", "text": "Swarm hanging from an oak branch",
         "source": {"url": "https://meadow.example/photos", "name": "Meadow", "title": "Photos"}}
        """;
  }

  private MvcTestResult create(Account account, String json) {
    return mvc.post()
        .uri("/api/v1/saves")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult createWithImage(Account account, String json, byte[] image, String type) {
    return mvc.post()
        .multipart()
        .uri("/api/v1/saves")
        .file(
            new MockMultipartFile(
                "save",
                "",
                MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8)))
        .file(new MockMultipartFile("image", "clip", type, image))
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult list(Account account, String query) {
    return mvc.get()
        .uri("/api/v1/saves?" + query)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  /** The list with {@code q} as a parameter, so its characters reach the API as they are. */
  private MvcTestResult search(Account account, String q, String query) {
    return mvc.get()
        .uri("/api/v1/saves?" + query)
        .param("q", q)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult facets(Account account, String query) {
    return mvc.get()
        .uri("/api/v1/saves/facets?" + query)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult get(Account account, String id) {
    return mvc.get()
        .uri("/api/v1/saves/" + id)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult update(Account account, String id, String json) {
    return mvc.patch()
        .uri("/api/v1/saves/" + id)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult delete(Account account, String id) {
    return mvc.delete()
        .uri("/api/v1/saves/" + id)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private void assertInvalidSave(Account account, String json) {
    MvcTestResult result = create(account, json);
    assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(read(result, "$.code")).isEqualTo("INVALID_SAVE");
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

  private static List<String> texts(MvcTestResult page) {
    assertThat(page).hasStatusOk();
    return JsonPath.read(body(page), "$.items[*].text");
  }

  private int savesCount(Account account) {
    return jdbc.queryForObject(
        "SELECT saves_count FROM users WHERE id = ?", Integer.class, account.id());
  }

  private String imageKey(String saveId) {
    return jdbc.queryForObject(
        "SELECT image_key FROM saves WHERE id = ?", String.class, UUID.fromString(saveId));
  }

  /** Every file stored for the user's saves, whatever the database says. */
  private List<String> storedKeys(Account account) {
    return s3
        .listObjectsV2(request -> request.bucket(BUCKET).prefix("saves/" + account.id() + "/"))
        .contents()
        .stream()
        .map(object -> object.key())
        .toList();
  }

  private boolean exists(String key) {
    try {
      s3.headObject(request -> request.bucket(BUCKET).key(key));
      return true;
    } catch (S3Exception e) {
      assertThat(e.statusCode()).isEqualTo(404);
      return false;
    }
  }

  /** What a browser gets from the signed link. */
  private static HttpResponse<byte[]> get(String url) throws Exception {
    try (HttpClient http = HttpClient.newHttpClient()) {
      return http.send(
          HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
  }

  /** A small real image in the given ImageIO format. */
  private static byte[] image(String format) {
    BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
    image.setRGB(3, 3, 0xC67139);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      assertThat(ImageIO.write(image, format, out)).isTrue();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
