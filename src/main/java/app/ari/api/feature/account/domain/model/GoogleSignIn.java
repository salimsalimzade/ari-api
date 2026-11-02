package app.ari.api.feature.account.domain.model;

import java.net.URI;

/**
 * The start of a Google sign-in: where the browser goes, and the PKCE verifier that must come back
 * with Google's answer (the browser keeps it in a short-lived cookie), so only the browser that
 * started the sign-in can finish it.
 */
public record GoogleSignIn(URI url, String codeVerifier) {}
