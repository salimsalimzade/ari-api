package app.ari.api.common.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchTextTest {

  @Test
  void keepsTheWordsOfAnyLanguageInLowercase() {
    assertThat(SearchText.words("  Nest-site, ŞİFRƏ & bees!  "))
        .containsExactly("nest", "site", "şifrə", "bees");
    assertThat(SearchText.words("!!! ...")).isEmpty();
    assertThat(SearchText.words(null)).isEmpty();
    assertThat(SearchText.words("a b c d e f g h i j k l")).hasSize(10);
  }

  @Test
  void everyWordMustStartAWord() {
    assertThat(SearchText.prefixQuery("swarm Nest")).isEqualTo("swarm:* & nest:*");
    // tsquery's own syntax never gets in.
    assertThat(SearchText.prefixQuery("a & (b | !c):*")).isEqualTo("a:* & b:* & c:*");
    assertThat(SearchText.prefixQuery("&|!")).isNull();
  }

  @Test
  void theWholeQueryAnywhereWithLikesCharactersEscaped() {
    assertThat(SearchText.containsPattern(" Nest Site ")).isEqualTo("%nest site%");
    assertThat(SearchText.containsPattern("50%_off\\")).isEqualTo("%50\\%\\_off\\\\%");
  }
}
