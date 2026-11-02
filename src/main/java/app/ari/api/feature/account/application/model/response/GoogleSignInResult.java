package app.ari.api.feature.account.application.model.response;

import java.net.URI;

/**
 * Where the browser goes once Google sent it back: the Library, the welcome page to choose a
 * username, or the log-in page with {@code ?error=google}. {@code tokens} sign it in; null when the
 * sign-in failed.
 */
public record GoogleSignInResult(URI location, AuthTokens tokens) {}
