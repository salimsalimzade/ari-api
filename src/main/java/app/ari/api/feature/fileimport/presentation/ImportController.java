package app.ari.api.feature.fileimport.presentation;

import app.ari.api.feature.fileimport.application.model.request.PhotoImportRequest;
import app.ari.api.feature.fileimport.application.model.request.ReviewRequest;
import app.ari.api.feature.fileimport.application.model.response.ImportResponse;
import app.ari.api.feature.fileimport.application.model.response.ImportsResponse;
import app.ari.api.feature.fileimport.application.model.response.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

@Tag(
    name = "Imports",
    description =
        "PDF and Word files: uploaded, read in the background for their highlights, underlines,"
            + " drawings, notes and comments, then reviewed into the Library; and photos, read in"
            + " the browser. Everyone has 50 MB of upload space, in files of any size that fit.")
@RequestMapping("/api/v1/imports")
public interface ImportController {

  @Operation(
      summary = "The Imports page",
      description =
          "Every file that isn't dismissed, newest first (at most 200), and the upload space."
              + " Poll it every 2 seconds while a file is QUEUED or READING.")
  @ApiResponse(responseCode = "200", description = "The files and the space")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @GetMapping
  ImportsResponse list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt);

  @Operation(
      summary = "Import a file",
      description =
          "Multipart, the file in the file part: a PDF or a Word DOCX, recognized by its bytes, of"
              + " any size that fits in the space left. It is read in the background.")
  @ApiResponse(responseCode = "202", description = "The import, QUEUED")
  @ApiResponse(
      responseCode = "400",
      description =
          "UNSUPPORTED_FILE (not a PDF or DOCX) or NO_SPACE_LEFT (the upload space is too full)")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(
      responseCode = "413",
      description = "FILE_TOO_LARGE: over 50 MB, more than the whole upload space")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  ResponseEntity<ImportResponse> upload(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @RequestPart("file") MultipartFile file);

  @Operation(
      summary = "Import a photo's marks",
      description =
          "The browser reads the photo (OCR) and finds the words marked on it: highlighted,"
              + " underlined, crossed out or drawn around with a pen. It sends them, not the photo."
              + " The import is READY at once, each mark in its color (mapped to the five as a"
              + " file's are) and ticked as a file's would be (a crossed-out one isn't), for the"
              + " same review; EMPTY when none were found. The photo's size counts against the"
              + " upload space.")
  @ApiResponse(responseCode = "201", description = "The import, READY with its marks, or EMPTY")
  @ApiResponse(
      responseCode = "400",
      description = "VALIDATION_FAILED, or NO_SPACE_LEFT (the upload space is too full)")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(
      responseCode = "413",
      description = "FILE_TOO_LARGE: over 50 MB, more than the whole upload space")
  @PostMapping(path = "/photo", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ImportResponse> importPhoto(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody PhotoImportRequest request);

  @Operation(
      summary = "One import",
      description = "With its marks once it is READY or SAVED, for the review.")
  @ApiResponse(responseCode = "200", description = "The import")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @GetMapping("/{id}")
  ImportResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id);

  @Operation(
      summary = "Save the review",
      description =
          "Ticked marks without a save become saves from the file's source (IMPORT, or an image"
              + " clip for a handwritten mark's picture), with the tag and color given; saved"
              + " marks that are unticked are removed from the Library. The title and author"
              + " change the source. The import becomes SAVED, and can be reviewed again.")
  @ApiResponse(responseCode = "200", description = "How many saves were added and removed")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @ApiResponse(responseCode = "409", description = "INVALID_IMPORT_STATE: it isn't READY or SAVED")
  @PutMapping("/{id}/review")
  ReviewResponse review(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ReviewRequest request);

  @Operation(summary = "Read a failed file again", description = "It goes back to QUEUED.")
  @ApiResponse(responseCode = "202", description = "The import, QUEUED")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @ApiResponse(responseCode = "409", description = "INVALID_IMPORT_STATE: it isn't FAILED")
  @PostMapping("/{id}/retry")
  ResponseEntity<ImportResponse> retry(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id);

  @Operation(
      summary = "Hide a file",
      description = "A FAILED, EMPTY or DISCARDED file leaves the Imports page.")
  @ApiResponse(responseCode = "204", description = "Hidden")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @ApiResponse(responseCode = "409", description = "INVALID_IMPORT_STATE")
  @PostMapping("/{id}/dismiss")
  ResponseEntity<Void> dismiss(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id);

  @Operation(
      summary = "Discard a file before its review",
      description = "Its marks and source go; nothing reaches the Library. It becomes DISCARDED.")
  @ApiResponse(responseCode = "204", description = "Discarded")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @ApiResponse(responseCode = "409", description = "INVALID_IMPORT_STATE: it isn't READY")
  @DeleteMapping("/{id}")
  ResponseEntity<Void> discard(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id);
}
