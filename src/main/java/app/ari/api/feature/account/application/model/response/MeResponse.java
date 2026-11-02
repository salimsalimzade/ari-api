package app.ari.api.feature.account.application.model.response;

import app.ari.api.feature.account.domain.model.Plan;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The signed-in user, as returned by {@code GET /api/v1/me} and the sign-in endpoints. {@code
 * email} is the verified email and {@code pendingEmail} the one waiting for its link; both are left
 * out when there is none. {@code photoUrl} shows the profile photo for an hour; left out without
 * one. {@code needsUsername} is true for a Google user until they confirm or change the username
 * made from their email ({@code PUT /me}); the website asks them first.
 */
public record MeResponse(
    UUID id,
    String username,
    boolean needsUsername,
    String fullName,
    LocalDate birthDate,
    String email,
    String pendingEmail,
    String photoUrl,
    Plan plan,
    int savesCount) {}
