package app.ari.api.feature.account.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code PUT /api/v1/me/email}. */
public record EmailRequest(
    @Schema(description = "Stored in lowercase", example = "ada@example.com")
        @NotBlank @Email @Size(max = 320) String email) {}
