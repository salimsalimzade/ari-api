package app.ari.api.feature.save.presentation;

import app.ari.api.common.response.PageResponse;
import app.ari.api.feature.save.application.model.request.CreateSaveRequest;
import app.ari.api.feature.save.application.model.request.ListSavesRequest;
import app.ari.api.feature.save.application.model.request.SaveFilterRequest;
import app.ari.api.feature.save.application.model.request.UpdateSaveRequest;
import app.ari.api.feature.save.application.model.response.FacetsResponse;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(
    name = "Saves",
    description =
        "The Library: highlights and clips saved from web pages, each with its source and tags."
            + " Every request is about the signed-in user's own saves.")
@RequestMapping("/api/v1/saves")
public interface SaveController {

  @Operation(
      summary = "Save a highlight or a table clip",
      description =
          "The source is found by its address, or created the first time: saving from the same"
              + " page again reuses it. An image clip goes in the multipart variant below.")
  @ApiResponse(responseCode = "201", description = "The save, with its source")
  @ApiResponse(
      responseCode = "400",
      description =
          "VALIDATION_FAILED, INVALID_SAVE (what the kind needs is missing or not allowed) or"
              + " INVALID_URL")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<SaveResponse> create(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody CreateSaveRequest request);

  @Operation(
      summary = "Save an image clip",
      description =
          "Multipart: the same JSON as above in the save part (kind CLIP, clipType IMAGE), and the"
              + " picture in the image part: a JPEG, PNG or WebP of at most 2 MB, recognized by"
              + " its bytes. The picture is stored first and removed again if the save fails.")
  @ApiResponse(responseCode = "201", description = "The save, with imageUrl for the next hour")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED, INVALID_SAVE, INVALID_IMAGE or INVALID_URL")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE: over 2 MB")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<SaveResponse> createWithImage(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestPart("save") CreateSaveRequest save,
      @RequestPart("image") MultipartFile image);

  @Operation(
      summary = "The Library, newest first",
      description =
          "Keyset paging: send nextCursor back as cursor for the next page. It is left out on the"
              + " last page. The first page is 40 saves unless limit says otherwise. With"
              + " order=PAGE and a source, a source's saves come in page order instead, as its"
              + " page on the website lists them.")
  @ApiResponse(responseCode = "200", description = "One page of saves")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED, or INVALID_CURSOR (also a cursor of the other order)")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @GetMapping
  PageResponse<SaveResponse> list(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @ParameterObject @Valid ListSavesRequest request);

  @Operation(
      summary = "What the Library's filters offer",
      description =
          "Counts for the type tabs (among the saves that match the filters) and the tag, color"
              + " and source choices (over the whole Library).")
  @ApiResponse(responseCode = "200", description = "The counts")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @GetMapping("/facets")
  FacetsResponse facets(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @ParameterObject SaveFilterRequest filter);

  @Operation(
      summary = "One save, with its source and tags",
      description = "For a page that shows a single save, such as the Library's reading view.")
  @ApiResponse(responseCode = "200", description = "The save")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @GetMapping("/{id}")
  SaveResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id);

  @Operation(
      summary = "Change a save's color, comment or tags",
      description = "A field that is left out stays as it is.")
  @ApiResponse(responseCode = "200", description = "The updated save")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED, or INVALID_SAVE: a clip has no color")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @PatchMapping("/{id}")
  SaveResponse update(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateSaveRequest request);

  @Operation(summary = "Delete a save", description = "An image clip's picture is deleted too.")
  @ApiResponse(responseCode = "204", description = "Deleted")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id);
}
