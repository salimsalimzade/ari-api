package app.ari.api.feature.note.presentation;

import app.ari.api.feature.note.application.model.request.ListNotesRequest;
import app.ari.api.feature.note.application.model.request.NoteRequest;
import app.ari.api.feature.note.application.model.response.NoteResponse;
import app.ari.api.feature.note.application.model.response.NoteSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
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

@Tag(
    name = "Notes",
    description =
        "The user's notes: paragraphs with quotes of their saves between them. A quote keeps only"
            + " its save, so it shows the save as it is now and goes when the save is deleted."
            + " Every request is about the signed-in user's own notes.")
@RequestMapping("/api/v1/notes")
public interface NoteController {

  @Operation(
      summary = "The Notes page, the note changed most recently first",
      description =
          "Every note, or those a search (q) finds, without its text: the title, the saves it"
              + " quotes and its times.")
  @ApiResponse(responseCode = "200", description = "The notes")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @GetMapping
  List<NoteSummaryResponse> list(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @ParameterObject @Valid ListNotesRequest request);

  @Operation(
      summary = "Start a note",
      description =
          "Empty, with quotes (Add to note), or a copy of another note's blocks (Duplicate).")
  @ApiResponse(responseCode = "201", description = "The note")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or INVALID_NOTE")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @PostMapping
  ResponseEntity<NoteResponse> create(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody NoteRequest request);

  @Operation(summary = "One note, with its blocks and the saves it quotes")
  @ApiResponse(responseCode = "200", description = "The note")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @GetMapping("/{id}")
  NoteResponse get(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id);

  @Operation(
      summary = "Save a note as it is now",
      description =
          "The website saves as you type: the title and every block replace what was there, in"
              + " one transaction.")
  @ApiResponse(responseCode = "200", description = "The note as saved")
  @ApiResponse(responseCode = "400", description = "VALIDATION_FAILED or INVALID_NOTE")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @PutMapping("/{id}")
  NoteResponse replace(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody NoteRequest request);

  @Operation(summary = "Delete a note", description = "The saves it quotes stay in the Library.")
  @ApiResponse(responseCode = "204", description = "Deleted")
  @ApiResponse(responseCode = "401", description = "UNAUTHORIZED")
  @ApiResponse(responseCode = "404", description = "NOT_FOUND, or it is someone else's")
  @DeleteMapping("/{id}")
  ResponseEntity<Void> delete(
      @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id);
}
