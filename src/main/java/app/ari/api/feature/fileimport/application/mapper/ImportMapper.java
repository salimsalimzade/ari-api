package app.ari.api.feature.fileimport.application.mapper;

import app.ari.api.feature.fileimport.application.model.response.ImportMarkResponse;
import app.ari.api.feature.fileimport.application.model.response.ImportResponse;
import app.ari.api.feature.fileimport.application.model.response.ImportsResponse;
import app.ari.api.feature.fileimport.domain.model.FileImport;
import app.ari.api.feature.fileimport.domain.model.ImportMark;
import app.ari.api.feature.fileimport.domain.model.UploadSpace;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import java.util.List;
import org.mapstruct.Mapper;

/** Written out: an import and its source share property names MapStruct can't tell apart. */
@Mapper
public interface ImportMapper {

  /** {@code source} is null until marks are found; {@code marks} null when they aren't sent. */
  default ImportResponse toResponse(
      FileImport fileImport, SourceResponse source, List<ImportMarkResponse> marks) {
    return new ImportResponse(
        fileImport.id(),
        fileImport.fileName(),
        fileImport.sizeBytes(),
        fileImport.status(),
        fileImport.errorCode(),
        fileImport.marksCount(),
        fileImport.keptCount(),
        source == null ? null : source.id(),
        source == null ? null : source.title(),
        source == null ? null : source.author(),
        fileImport.readAt(),
        fileImport.reviewedAt(),
        fileImport.createdAt(),
        marks);
  }

  /** {@code imageUrl} is the signed link to its picture, or null. */
  default ImportMarkResponse toMarkResponse(ImportMark mark, String imageUrl) {
    return new ImportMarkResponse(
        mark.id(),
        mark.type(),
        mark.color(),
        mark.fileColor(),
        mark.text(),
        mark.pageNumber(),
        mark.comment(),
        imageUrl,
        mark.selected(),
        mark.saveId());
  }

  default ImportsResponse.UploadSpaceResponse toSpaceResponse(UploadSpace space) {
    return new ImportsResponse.UploadSpaceResponse(space.usedBytes(), UploadSpace.LIMIT_BYTES);
  }
}
