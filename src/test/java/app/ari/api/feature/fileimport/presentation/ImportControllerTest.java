package app.ari.api.feature.fileimport.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import app.ari.api.feature.fileimport.ImportFiles;
import app.ari.api.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * Importing end to end: the upload goes through the outbox and RabbitMQ to the consumer, which
 * reads the file from MinIO; the review saves into the Library.
 */
@IntegrationTest
class ImportControllerTest {

  private static final String BUCKET = "ari";
  private static final Duration READING = Duration.ofSeconds(30);

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private S3Client s3;

  @Test
  void aPdfIsReadAndItsReviewSavesTheTickedMarks() {
    Account account = signUp();

    MvcTestResult uploaded = upload(account, "folder/swarms.pdf", ImportFiles.annotatedPdf());

    assertThat(uploaded).hasStatus(HttpStatus.ACCEPTED);
    assertThat(text(uploaded, "$.status")).isEqualTo("QUEUED");
    assertThat(text(uploaded, "$.fileName")).isEqualTo("swarms.pdf");
    String id = text(uploaded, "$.id");
    String storageKey = "imports/" + account.id() + "/" + id;
    awaitStatus(id, "READY");
    // The file goes once the reading's transaction has committed, a moment after the status.
    await().atMost(READING).until(() -> !exists(storageKey));

    MvcTestResult found = get(account, id);
    assertThat(found).hasStatusOk();
    assertThat(text(found, "$.title")).isEqualTo("How Honeybees Choose a New Home");
    assertThat(text(found, "$.author")).isEqualTo("J. Arden");
    assertThat(JsonPath.<Integer>read(body(found), "$.marksCount")).isEqualTo(6);
    assertThat(JsonPath.<List<String>>read(body(found), "$.marks[*].type"))
        .containsExactly("HIGHLIGHT", "STRIKE", "UNDERLINE", "NOTE", "DRAWN", "HANDWRITTEN");
    assertThat(JsonPath.<List<Boolean>>read(body(found), "$.marks[*].selected"))
        .containsExactly(true, false, true, false, true, false);
    assertThat(JsonPath.<List<Integer>>read(body(found), "$.marks[*].pageNumber"))
        .containsExactly(1, 1, 2, 2, 2, 2);
    assertThat(text(found, "$.marks[2].fileColor")).isEqualTo("RED");
    assertThat(text(found, "$.marks[5].imageUrl")).contains(BUCKET);
    List<String> markIds = JsonPath.read(body(found), "$.marks[*].id");

    // Tick the highlight, the underline and the handwriting; leave the drawing out.
    MvcTestResult reviewed =
        review(
            account,
            id,
            """
            {"title": "Swarms", "author": "", "tag": "swarm",
             "selectedMarkIds": ["%s", "%s", "%s"]}
            """
                .formatted(markIds.get(0), markIds.get(2), markIds.get(5)));

    assertThat(reviewed).hasStatusOk();
    assertThat(JsonPath.<Integer>read(body(reviewed), "$.added")).isEqualTo(3);
    assertThat(JsonPath.<Integer>read(body(reviewed), "$.removed")).isZero();
    List<Map<String, Object>> saves =
        jdbc.queryForList(
            """
            SELECT s.kind, s.color, s.clip_type, s.text, s.comment, s.page_number, s.image_key,
                   src.kind AS source_kind, src.title, src.author
            FROM saves s JOIN sources src ON src.id = s.source_id
            WHERE s.user_id = ? ORDER BY s.page_number, s.kind DESC
            """,
            account.id());
    assertThat(saves)
        .extracting(row -> row.get("kind"))
        .containsExactly("IMPORT", "IMPORT", "CLIP");
    assertThat(saves.get(0))
        .containsEntry("color", "GREEN")
        .containsEntry("text", "Scouts dance for the best site.")
        .containsEntry("comment", "Check the 2019 study")
        .containsEntry("page_number", 1)
        .containsEntry("source_kind", "FILE")
        .containsEntry("title", "Swarms")
        .containsEntry("author", null);
    assertThat(saves.get(2)).containsEntry("clip_type", "IMAGE").containsEntry("text", "Swarms");
    assertThat(exists((String) saves.get(2).get("image_key"))).isTrue();
    assertThat(savesCount(account)).isEqualTo(3);
    assertThat(
            jdbc.queryForList(
                "SELECT t.name FROM tags t JOIN save_tags st ON st.tag_id = t.id"
                    + " WHERE t.user_id = ?",
                String.class,
                account.id()))
        .containsOnly("swarm");

    MvcTestResult saved = get(account, id);
    assertThat(text(saved, "$.status")).isEqualTo("SAVED");
    assertThat(JsonPath.<Integer>read(body(saved), "$.keptCount")).isEqualTo(3);

    // Editing the review: unticking a saved mark removes its save.
    MvcTestResult edited =
        review(
            account,
            id,
            """
            {"title": "Swarms", "selectedMarkIds": ["%s", "%s"]}
            """
                .formatted(markIds.get(2), markIds.get(5)));
    assertThat(JsonPath.<Integer>read(body(edited), "$.added")).isZero();
    assertThat(JsonPath.<Integer>read(body(edited), "$.removed")).isEqualTo(1);
    assertThat(savesCount(account)).isEqualTo(2);
  }

  @Test
  void aWordFileIsReadToo() {
    Account account = signUp();

    String id = text(upload(account, "notes.docx", ImportFiles.annotatedDocx()), "$.id");
    awaitStatus(id, "READY");

    MvcTestResult found = get(account, id);
    assertThat(text(found, "$.title")).isEqualTo("Swarm notes");
    assertThat(JsonPath.<List<String>>read(body(found), "$.marks[*].text"))
        .containsExactly(
            "scouts dance for sites",
            "Bees always choose the closest.",
            "A pink one.",
            "Quorum at fifteen scouts.");
  }

  @Test
  void aFileWithoutMarksIsEmptyAndCanBeDismissed() {
    Account account = signUp();
    String id = text(upload(account, "plain.pdf", ImportFiles.plainPdf()), "$.id");
    awaitStatus(id, "EMPTY");

    assertThat(post(account, "/api/v1/imports/" + id + "/dismiss"))
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(JsonPath.<List<String>>read(body(list(account)), "$.items[*].id")).isEmpty();
  }

  @Test
  void aDamagedFileFailsAtOnceAndCanBeReadAgain() {
    Account account = signUp();
    String id = text(upload(account, "broken.pdf", ImportFiles.brokenPdf()), "$.id");

    awaitStatus(id, "FAILED");
    assertThat(text(get(account, id), "$.errorCode")).isEqualTo("UNREADABLE");
    assertThat(exists("imports/" + account.id() + "/" + id)).isTrue();

    MvcTestResult retried = post(account, "/api/v1/imports/" + id + "/retry");
    assertThat(retried).hasStatus(HttpStatus.ACCEPTED);
    assertThat(text(retried, "$.status")).isEqualTo("QUEUED");
    awaitStatus(id, "FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT attempts FROM imports WHERE id = ?", Integer.class, UUID.fromString(id)))
        .isEqualTo(2);
  }

  @Test
  void discardingDropsTheMarksAndTheSource() {
    Account account = signUp();
    String id = text(upload(account, "swarms.pdf", ImportFiles.annotatedPdf()), "$.id");
    awaitStatus(id, "READY");

    assertThat(
            mvc.delete()
                .uri("/api/v1/imports/" + id)
                .cookie(new Cookie(ACCESS, account.accessToken()))
                .exchange())
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(text(get(account, id), "$.status")).isEqualTo("DISCARDED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM import_marks WHERE import_id = ?",
                Integer.class,
                UUID.fromString(id)))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sources WHERE user_id = ?", Integer.class, account.id()))
        .isZero();
    // A discarded file no longer takes space.
    assertThat(JsonPath.<Integer>read(body(list(account)), "$.space.usedBytes")).isZero();
  }

  @Test
  void onlyPdfAndWordFilesAreTaken() {
    Account account = signUp();

    MvcTestResult refused =
        upload(account, "notes.pdf", "just text".getBytes(StandardCharsets.UTF_8));

    assertThat(refused).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(text(refused, "$.code")).isEqualTo("UNSUPPORTED_FILE");
  }

  @Test
  void aFileOfAnySizeIsTakenWhileItFits() {
    Account account = signUp();
    byte[] big = new byte[20 * 1024 * 1024];
    System.arraycopy(ImportFiles.plainPdf(), 0, big, 0, 8);

    MvcTestResult taken = upload(account, "big.pdf", big);

    assertThat(taken).hasStatus(HttpStatus.ACCEPTED);
    assertThat(JsonPath.<Number>read(body(list(account)), "$.space.usedBytes").longValue())
        .isEqualTo(big.length);
  }

  @Test
  void aFileBiggerThanTheWholeSpaceIsRefused() {
    Account account = signUp();
    byte[] big = new byte[50 * 1024 * 1024 + 1];
    System.arraycopy(ImportFiles.plainPdf(), 0, big, 0, 8);

    MvcTestResult refused = upload(account, "huge.pdf", big);

    assertThat(refused).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(text(refused, "$.code")).isEqualTo("FILE_TOO_LARGE");
  }

  @Test
  void everyoneHasFiftyMegabytesOfUploadSpace() {
    Account account = signUp();
    long almostFull = 50L * 1024 * 1024 - 100;
    jdbc.update(
        "INSERT INTO imports (id, user_id, file_name, content_type, size_bytes, status)"
            + " VALUES (?, ?, 'old.pdf', 'application/pdf', ?, 'SAVED')",
        UUID.randomUUID(),
        account.id(),
        almostFull);

    MvcTestResult page = list(account);
    assertThat(JsonPath.<Number>read(body(page), "$.space.usedBytes").longValue())
        .isEqualTo(almostFull);
    assertThat(JsonPath.<Number>read(body(page), "$.space.limitBytes").longValue())
        .isEqualTo(50L * 1024 * 1024);

    MvcTestResult refused = upload(account, "swarms.pdf", ImportFiles.annotatedPdf());
    assertThat(refused).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(text(refused, "$.code")).isEqualTo("NO_SPACE_LEFT");
  }

  @Test
  void aPhotosMarksAreReadyToReviewAtOnce() {
    Account account = signUp();

    MvcTestResult imported =
        photo(
            account,
            """
            {"fileName": "camera/IMG_0412.jpg", "contentType": "image/jpeg", "sizeBytes": 2048,
             "marks": [
               {"type": "HIGHLIGHT", "text": "  A colony splits in two. ", "color": "#F2E25C"},
               {"type": "HIGHLIGHT", "text": "The old queen leaves.", "color": "#FF8A3D"},
               {"type": "UNDERLINE", "text": "Scouts dance for the best site.",
                "color": "#2B4FD8"},
               {"type": "STRIKE", "text": "Bees choose the closest."},
               {"type": "DRAWN", "text": "Quorum", "color": "#1A1A1A"}
             ]}
            """);

    assertThat(imported).hasStatus(HttpStatus.CREATED);
    assertThat(text(imported, "$.status")).isEqualTo("READY");
    assertThat(text(imported, "$.fileName")).isEqualTo("IMG_0412.jpg");
    assertThat(text(imported, "$.title")).isEqualTo("IMG_0412.jpg");
    assertThat(JsonPath.<List<String>>read(body(imported), "$.marks[*].type"))
        .containsExactly("HIGHLIGHT", "HIGHLIGHT", "UNDERLINE", "STRIKE", "DRAWN");
    assertThat(JsonPath.<List<String>>read(body(imported), "$.marks[*].color"))
        .containsExactly("YELLOW", "YELLOW", "BLUE", "YELLOW", "YELLOW");
    assertThat(text(imported, "$.marks[1].fileColor")).isEqualTo("ORANGE");
    assertThat(text(imported, "$.marks[0].text")).isEqualTo("A colony splits in two.");
    assertThat(JsonPath.<List<Boolean>>read(body(imported), "$.marks[*].selected"))
        .containsExactly(true, true, true, false, true);
    assertThat(JsonPath.<Number>read(body(list(account)), "$.space.usedBytes").longValue())
        .isEqualTo(2048);

    List<String> markIds = JsonPath.read(body(imported), "$.marks[*].id");
    MvcTestResult reviewed =
        review(
            account,
            text(imported, "$.id"),
            """
            {"title": "Field notes", "selectedMarkIds": ["%s"]}
            """
                .formatted(markIds.get(2)));

    assertThat(reviewed).hasStatusOk();
    assertThat(JsonPath.<Integer>read(body(reviewed), "$.added")).isEqualTo(1);
    assertThat(savesCount(account)).isEqualTo(1);
    assertThat(
            jdbc.queryForMap(
                "SELECT s.kind, s.color, s.text, src.title FROM saves s"
                    + " JOIN sources src ON src.id = s.source_id WHERE s.user_id = ?",
                account.id()))
        .containsEntry("kind", "IMPORT")
        .containsEntry("color", "BLUE")
        .containsEntry("text", "Scouts dance for the best site.")
        .containsEntry("title", "Field notes");
  }

  @Test
  void aPhotoWithoutMarksIsEmpty() {
    Account account = signUp();

    MvcTestResult imported =
        photo(
            account,
            """
            {"fileName": "plain.png", "contentType": "image/png", "sizeBytes": 10, "marks": []}
            """);

    assertThat(imported).hasStatus(HttpStatus.CREATED);
    assertThat(text(imported, "$.status")).isEqualTo("EMPTY");
    // Null fields are left out: an empty photo has no source.
    assertThat(body(imported)).doesNotContain("sourceId");
  }

  @Test
  void aPhotoNeedsAnImageType() {
    Account account = signUp();

    MvcTestResult refused =
        photo(
            account,
            """
            {"fileName": "notes.pdf", "contentType": "application/pdf", "sizeBytes": 10,
             "marks": []}
            """);

    assertThat(refused).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(text(refused, "$.code")).isEqualTo("VALIDATION_FAILED");
  }

  @Test
  void aPhotoTakesItsSizeOfTheUploadSpace() {
    Account account = signUp();

    MvcTestResult refused =
        photo(
            account,
            """
            {"fileName": "huge.png", "contentType": "image/png", "sizeBytes": 52428801,
             "marks": []}
            """);

    assertThat(refused).hasStatus(HttpStatus.CONTENT_TOO_LARGE);
    assertThat(text(refused, "$.code")).isEqualTo("FILE_TOO_LARGE");
  }

  @Test
  void someoneElsesImportIsNotFound() {
    Account owner = signUp();
    Account other = signUp();
    String id = text(upload(owner, "plain.pdf", ImportFiles.plainPdf()), "$.id");

    assertThat(get(other, id)).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(post(other, "/api/v1/imports/" + id + "/retry")).hasStatus(HttpStatus.NOT_FOUND);
  }

  @Test
  void aFileStillBeingReadCannotBeReviewed() {
    Account account = signUp();
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO imports (id, user_id, file_name, content_type, size_bytes, status)"
            + " VALUES (?, ?, 'slow.pdf', 'application/pdf', 10, 'READING')",
        id,
        account.id());

    MvcTestResult refused = review(account, id.toString(), "{\"selectedMarkIds\": []}");

    assertThat(refused).hasStatus(HttpStatus.CONFLICT);
    assertThat(text(refused, "$.code")).isEqualTo("INVALID_IMPORT_STATE");
  }

  // — Helpers —

  private record Account(UUID id, String accessToken) {}

  private Account signUp() {
    String username = "imp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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

  private MvcTestResult upload(Account account, String name, byte[] content) {
    return mvc.post()
        .multipart()
        .uri("/api/v1/imports")
        .file(new MockMultipartFile("file", name, "application/octet-stream", content))
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult photo(Account account, String json) {
    return mvc.post()
        .uri("/api/v1/imports/photo")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult list(Account account) {
    return mvc.get()
        .uri("/api/v1/imports")
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult get(Account account, String id) {
    return mvc.get()
        .uri("/api/v1/imports/" + id)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult post(Account account, String uri) {
    return mvc.post().uri(uri).cookie(new Cookie(ACCESS, account.accessToken())).exchange();
  }

  private MvcTestResult review(Account account, String id, String json) {
    return mvc.put()
        .uri("/api/v1/imports/" + id + "/review")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  /** The consumer reads in the background: wait for it. */
  private void awaitStatus(String id, String status) {
    await()
        .atMost(READING)
        .until(
            () ->
                status.equals(
                    jdbc.queryForObject(
                        "SELECT status FROM imports WHERE id = ?",
                        String.class,
                        UUID.fromString(id))));
  }

  private int savesCount(Account account) {
    return jdbc.queryForObject(
        "SELECT saves_count FROM users WHERE id = ?", Integer.class, account.id());
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

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String text(MvcTestResult result, String path) {
    return JsonPath.read(body(result), path);
  }
}
