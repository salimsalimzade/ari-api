package app.ari.api.feature.fileimport.application.model.response;

import app.ari.api.feature.fileimport.domain.model.ImportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** An uploaded file and how far it got. */
public record ImportResponse(
    UUID id,
    String fileName,
    long sizeBytes,
    @Schema(
            description =
                "QUEUED and READING while it is read; then READY to review, EMPTY or FAILED;"
                    + " SAVED once reviewed, DISCARDED when dropped")
        ImportStatus status,
    @Schema(description = "FAILED only: UNREADABLE, ENCRYPTED or READ_FAILED") String errorCode,
    @Schema(description = "How many marks reading found") int marksCount,
    @Schema(description = "How many of them are in the Library") int keptCount,
    @Schema(description = "The file's source, once marks are found") UUID sourceId,
    @Schema(description = "The source's title: the file's own, or its name") String title,
    String author,
    Instant readAt,
    @Schema(description = "When the review was first saved") Instant reviewedAt,
    Instant createdAt,
    @Schema(description = "Only from GET /imports/{id}, once READY or SAVED")
        List<ImportMarkResponse> marks) {}
