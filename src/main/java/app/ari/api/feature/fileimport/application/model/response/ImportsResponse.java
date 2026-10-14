package app.ari.api.feature.fileimport.application.model.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The Imports page: the files not dismissed, newest first, and the upload space. */
public record ImportsResponse(List<ImportResponse> items, UploadSpaceResponse space) {

  /**
   * Everyone has the same space for now, in files of any size that fit; billing will give plans
   * their own.
   */
  public record UploadSpaceResponse(
      @Schema(description = "Files queued, being read, ready or saved") long usedBytes,
      long limitBytes) {}
}
