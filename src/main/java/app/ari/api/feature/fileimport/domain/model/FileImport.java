package app.ari.api.feature.fileimport.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * An uploaded file and what became of it. {@code storageKey} is set while the file is in storage:
 * until it is read, and on after a failure so Retry can read it again. {@code sourceId} is the
 * file's source, made when reading found marks. {@code marksCount} and {@code keptCount} (the marks
 * saved to the Library) are counted when it is read from the database.
 */
public record FileImport(
    UUID id,
    UUID userId,
    UUID sourceId,
    String fileName,
    String contentType,
    long sizeBytes,
    String storageKey,
    ImportStatus status,
    int attempts,
    String errorCode,
    Instant readAt,
    Instant reviewedAt,
    Instant dismissedAt,
    Instant createdAt,
    Instant updatedAt,
    int marksCount,
    int keptCount) {}
