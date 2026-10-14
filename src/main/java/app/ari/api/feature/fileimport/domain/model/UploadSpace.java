package app.ari.api.feature.fileimport.domain.model;

/**
 * How much of the upload space the user's files take. Everyone has 50 MB, in files of any size that
 * fit in what is left, until billing gives plans their own (docs/database.md section 3).
 */
public record UploadSpace(long usedBytes) {

  public static final long LIMIT_BYTES = 50L * 1024 * 1024;

  public long leftBytes() {
    return Math.max(0, LIMIT_BYTES - usedBytes);
  }

  public boolean fits(long sizeBytes) {
    return sizeBytes <= leftBytes();
  }
}
