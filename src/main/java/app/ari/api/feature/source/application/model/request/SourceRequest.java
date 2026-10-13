package app.ari.api.feature.source.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The web page a save was made on. Saving again from the same page (the same address, whatever the
 * fragment) reuses its source and brings the name, title and author up to date.
 */
public record SourceRequest(
    @Schema(
            description =
                "http or https address of the page; the #fragment is dropped, so a text-fragment"
                    + " jump link may be sent as it is",
            example = "https://meadowquarterly.example/honeybees-new-home")
        @NotBlank @Size(max = 2048) String url,
    @Schema(description = "The site's name", example = "Meadow Quarterly")
        @NotBlank @Size(max = 255) String name,
    @Schema(description = "The page's title", example = "How Honeybees Choose a New Home")
        @NotBlank @Size(max = 500) String title,
    @Schema(description = "Left out when the page names none", example = "J. Arden")
        @Size(max = 255) String author) {}
