package app.ari.api.feature.account.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/v1/auth/extension/token}. */
public record ExtensionCodeRequest(
    @Schema(description = "The code the signed-in website handed to the extension")
        @NotBlank @Size(max = 100) String code) {}
