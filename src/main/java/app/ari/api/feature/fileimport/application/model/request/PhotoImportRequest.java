package app.ari.api.feature.fileimport.application.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/v1/imports/photo}: the marked text the browser found in a photo (OCR
 * happens there): highlighted, underlined, crossed out or drawn around, each with its color.
 */
public record PhotoImportRequest(
    @Schema(description = "The photo's file name", example = "page-12.jpg")
        @NotBlank @Size(max = 255) String fileName,
    @Schema(description = "The photo's type", example = "image/jpeg")
        @NotBlank @Pattern(regexp = "image/(jpeg|png|webp)")
        String contentType,
    @Schema(
            description = "The photo's size; it counts against the upload space like a file's",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @Positive long sizeBytes,
    @Schema(description = "The marks found, in the photo's order; empty when there are none")
        @NotNull @Size(max = 500) List<@NotNull @Valid PhotoMarkRequest> marks) {

  /** One mark: how the words were marked, the words, and the marker's or pen's color. */
  public record PhotoMarkRequest(
      @Schema(description = "How the words were marked") @NotNull PhotoMarkType type,
      @Schema(description = "The marked words", example = "A colony splits in two.")
          @NotBlank @Size(max = 10000) String text,
      @Schema(
              description =
                  "The marker's or pen's color as #RRGGBB, mapped to the nearest of the five like"
                      + " a file's; black, gray or left out is yellow",
              example = "#F2E25C")
          @Pattern(regexp = "#?[0-9A-Fa-f]{6}") String color) {}

  /** What a photo shows: a highlighter, a pen line under or through words, or a pen drawing. */
  public enum PhotoMarkType {
    HIGHLIGHT,
    UNDERLINE,
    STRIKE,
    DRAWN
  }
}
