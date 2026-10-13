package app.ari.api.feature.source.domain.model;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Optional;

/** The address of a web page as Ari stores it, so the same page always gives the same text. */
public final class SourceUrl {

  public static final int MAX_LENGTH = 2048;

  private SourceUrl() {}

  /**
   * An http or https address with a lowercase scheme and host and no fragment or login, and "/" for
   * an empty path; empty when it is anything else. The path and query are kept exactly as sent,
   * because sites tell pages apart by them.
   */
  public static Optional<String> normalize(String address) {
    if (address == null) {
      return Optional.empty();
    }
    URI uri;
    try {
      uri = new URI(address.strip());
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    String host = uri.getHost();
    if (!(scheme.equals("http") || scheme.equals("https")) || host == null || host.isEmpty()) {
      return Optional.empty();
    }
    StringBuilder normalized =
        new StringBuilder(scheme).append("://").append(host.toLowerCase(Locale.ROOT));
    if (uri.getPort() != -1) {
      normalized.append(':').append(uri.getPort());
    }
    String path = uri.getRawPath();
    normalized.append(path == null || path.isEmpty() ? "/" : path);
    if (uri.getRawQuery() != null) {
      normalized.append('?').append(uri.getRawQuery());
    }
    return normalized.length() > MAX_LENGTH ? Optional.empty() : Optional.of(normalized.toString());
  }
}
