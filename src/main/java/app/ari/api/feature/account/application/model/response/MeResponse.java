package app.ari.api.feature.account.application.model.response;

import app.ari.api.feature.account.domain.model.Plan;
import java.time.LocalDate;
import java.util.UUID;

/** The signed-in user, as returned by {@code GET /api/v1/me} and the sign-in endpoints. */
public record MeResponse(
    UUID id, String username, String fullName, LocalDate birthDate, Plan plan, int savesCount) {}
