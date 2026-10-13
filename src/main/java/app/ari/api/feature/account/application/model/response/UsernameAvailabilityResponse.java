package app.ari.api.feature.account.application.model.response;

/**
 * Answer of {@code GET /api/v1/auth/username-available}.
 *
 * @param username the name as it would be stored: trimmed and lowercase
 * @param available no account has it, in any case
 */
public record UsernameAvailabilityResponse(String username, boolean available) {}
