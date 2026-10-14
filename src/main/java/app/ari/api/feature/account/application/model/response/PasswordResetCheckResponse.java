package app.ari.api.feature.account.application.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Whose password a reset link changes. */
public record PasswordResetCheckResponse(@Schema(example = "ada.lovelace") String username) {}
