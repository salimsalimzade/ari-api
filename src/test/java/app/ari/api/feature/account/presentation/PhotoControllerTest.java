package app.ari.api.feature.account.presentation;

import static app.ari.api.feature.account.presentation.cookie.AuthCookies.ACCESS;
import static org.assertj.core.api.Assertions.assertThat;

import app.ari.api.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.imageio.ImageIO;
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

/** The profile photo in a real MinIO, shown through its signed link. */
@IntegrationTest
class PhotoControllerTest {

  private static final String BUCKET = "ari";

  @Autowired private MockMvcTester mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private S3Client s3;

  @Test
  void anUploadedPhotoIsStoredAndShownThroughItsSignedLink() throws Exception {
    Account account = signUp();
    byte[] photo = image("jpg");

    MvcTestResult result = uploadPhoto(account, photo, "image/jpeg");

    assertThat(result).hasStatusOk();
    String photoUrl = JsonPath.read(result.getResponse().getContentAsString(), "$.photoUrl");
    assertThat(avatarKey(account)).matches("avatars/" + account.id() + "/[0-9a-f-]{36}\\.jpg");
    HttpResponse<byte[]> shown = get(photoUrl);
    assertThat(shown.statusCode()).isEqualTo(200);
    assertThat(shown.body()).isEqualTo(photo);
    assertThat(shown.headers().firstValue("Content-Type")).contains("image/jpeg");
    assertThat(me(account)).bodyJson().extractingPath("$.photoUrl").isNotNull();
  }

  @Test
  void aNewPhotoReplacesTheOldFile() {
    Account account = signUp();
    uploadPhoto(account, image("jpg"), "image/jpeg");
    String oldKey = avatarKey(account);

    assertThat(uploadPhoto(account, image("png"), "image/png")).hasStatusOk();

    assertThat(avatarKey(account)).endsWith(".png").isNotEqualTo(oldKey);
    assertThat(exists(oldKey)).isFalse();
  }

  @Test
  void removingThePhotoDeletesItsFile() {
    Account account = signUp();
    uploadPhoto(account, image("jpg"), "image/jpeg");
    String key = avatarKey(account);

    assertThat(
            mvc.delete()
                .uri("/api/v1/me/photo")
                .cookie(new Cookie(ACCESS, account.accessToken()))
                .exchange())
        .hasStatus(HttpStatus.NO_CONTENT);

    assertThat(me(account)).bodyJson().doesNotHavePath("$.photoUrl");
    assertThat(avatarKey(account)).isNull();
    assertThat(exists(key)).isFalse();
  }

  @Test
  void onlyRealImagesAreAcceptedWhateverTheUploadClaims() {
    Account account = signUp();

    assertThat(
            uploadPhoto(
                account, "<svg onload=alert(1)/>".getBytes(StandardCharsets.UTF_8), "image/jpeg"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("INVALID_PHOTO");
    assertThat(avatarKey(account)).isNull();
  }

  @Test
  void aPhotoOverTwoMegabytesIsRefused() {
    Account account = signUp();
    byte[] tooBig = new byte[2 * 1024 * 1024 + 1];
    System.arraycopy(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0, tooBig, 0, 3);

    // MockMvc skips the servlet's own size limit, so this reaches the service's check.
    assertThat(uploadPhoto(account, tooBig, "image/jpeg")).hasStatus(HttpStatus.BAD_REQUEST);
    assertThat(avatarKey(account)).isNull();
  }

  @Test
  void needsASignedInUser() {
    assertThat(
            mvc.put()
                .multipart()
                .uri("/api/v1/me/photo")
                .file(new MockMultipartFile("photo", "me.jpg", "image/jpeg", image("jpg")))
                .exchange())
        .hasStatus(HttpStatus.UNAUTHORIZED);
  }

  private record Account(UUID id, String accessToken) {}

  private Account signUp() {
    String username = "photo_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
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

  private MvcTestResult uploadPhoto(Account account, byte[] photo, String contentType) {
    return mvc.put()
        .multipart()
        .uri("/api/v1/me/photo")
        .file(new MockMultipartFile("photo", "me", contentType, photo))
        .cookie(new Cookie(ACCESS, account.accessToken()))
        .exchange();
  }

  private MvcTestResult me(Account account) {
    return mvc.get().uri("/api/v1/me").cookie(new Cookie(ACCESS, account.accessToken())).exchange();
  }

  private String avatarKey(Account account) {
    return jdbc.queryForObject(
        "SELECT avatar_key FROM users WHERE id = ?", String.class, account.id());
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
