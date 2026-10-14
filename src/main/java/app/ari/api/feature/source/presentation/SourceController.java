package app.ari.api.feature.source.presentation;

import app.ari.api.common.response.PageResponse;
import app.ari.api.feature.source.application.model.request.ListSourcesRequest;
import app.ari.api.feature.source.application.model.response.SourceCountsResponse;
import app.ari.api.feature.source.application.model.response.SourceSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(
    name = "Sources",
    description =
        "The web pages and files the Library's saves come from, each with what is saved from it."
            + " A source with nothing saved from it any more is left out. Every request is about"
            + " the signed-in user's own sources.")
@RequestMapping("/api/v1/sources")
public interface SourceController {

  @Operation(
      summary = "The Sources page, the source saved from most recently first",
      description =
          "Keyset paging, as in the Library: send nextCursor back as cursor for the next page. It"
              + " is left out on the last page. The first page is 40 sources unless limit says"
              + " otherwise. A source's saves come from GET /api/v1/saves?source={id}.")
  @ApiResponse(responseCode = "200", description = "One page of sources")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or INVALID_CURSOR")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @GetMapping
  PageResponse<SourceSummaryResponse> list(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @ParameterObject @Valid ListSourcesRequest request);

  @Operation(
      summary = "How many sources there are, of each kind",
      description = "The counts of the Sources page's kind tabs.")
  @ApiResponse(responseCode = "200", description = "The counts")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @GetMapping("/counts")
  SourceCountsResponse counts(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt);

  @Operation(
      summary = "One source, with what is saved from it",
      description =
          "For a source's own page. Its saves, in page order, come from"
              + " GET /api/v1/saves?source={id}&order=PAGE.")
  @ApiResponse(responseCode = "200", description = "The source")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(
      responseCode = "404",
      description =
          "NOT_FOUND: no such source, someone else's, or nothing is saved from it any more")
  @GetMapping("/{id}")
  SourceSummaryResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id);
}
