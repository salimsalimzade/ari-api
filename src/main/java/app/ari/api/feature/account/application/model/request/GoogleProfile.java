package app.ari.api.feature.account.application.model.request;

/** What Google's ID token says about the person signing in. */
public record GoogleProfile(String subject, String email, boolean emailVerified, String name) {}
