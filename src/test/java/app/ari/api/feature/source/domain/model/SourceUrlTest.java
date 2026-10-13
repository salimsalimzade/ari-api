package app.ari.api.feature.source.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SourceUrlTest {

  @Test
  void dropsTheFragmentAndLowercasesTheSchemeAndHost() {
    assertThat(SourceUrl.normalize("HTTPS://Meadow.Example/Honeybees?id=7#:~:text=In%20late"))
        .contains("https://meadow.example/Honeybees?id=7");
  }

  @Test
  void keepsThePathAndQueryExactlyAsSent() {
    assertThat(SourceUrl.normalize("https://a.example/a%20b/c?x=1&y=%2F"))
        .contains("https://a.example/a%20b/c?x=1&y=%2F");
  }

  @Test
  void anEmptyPathIsTheRoot() {
    assertThat(SourceUrl.normalize("https://a.example")).contains("https://a.example/");
    assertThat(SourceUrl.normalize("https://a.example?x=1")).contains("https://a.example/?x=1");
  }

  @Test
  void keepsAPortAndDropsALogin() {
    assertThat(SourceUrl.normalize("http://user:secret@a.example:8080/p"))
        .contains("http://a.example:8080/p");
  }

  @Test
  void onlyHttpAndHttpsAddressesQualify() {
    assertThat(SourceUrl.normalize("ftp://a.example/file")).isEmpty();
    assertThat(SourceUrl.normalize("javascript:alert(1)")).isEmpty();
    assertThat(SourceUrl.normalize("/relative/path")).isEmpty();
    assertThat(SourceUrl.normalize("https:///no-host")).isEmpty();
    assertThat(SourceUrl.normalize("not a url")).isEmpty();
    assertThat(SourceUrl.normalize("")).isEmpty();
    assertThat(SourceUrl.normalize(null)).isEmpty();
  }

  @Test
  void anAddressOverTheLimitIsRefused() {
    assertThat(SourceUrl.normalize("https://a.example/" + "x".repeat(SourceUrl.MAX_LENGTH)))
        .isEmpty();
  }
}
