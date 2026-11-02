package app.ari.api.feature.account.application.model.response;

import java.net.URI;

/**
 * Where the browser goes to sign in with Google, and the PKCE verifier it must keep (in a cookie)
 * until Google sends it back.
 */
public record GoogleSignInStart(URI location, String codeVerifier) {}
