package app.ari.api.feature.fileimport.domain.service.impl;

import app.ari.api.common.client.ObjectStorage;
import app.ari.api.common.transaction.AfterCommit;
import app.ari.api.common.transaction.OnRollback;
import app.ari.api.feature.fileimport.domain.model.FileImport;
import app.ari.api.feature.fileimport.domain.model.FileKind;
import app.ari.api.feature.fileimport.domain.model.FileReadException;
import app.ari.api.feature.fileimport.domain.model.FoundFile;
import app.ari.api.feature.fileimport.domain.model.FoundMark;
import app.ari.api.feature.fileimport.domain.model.ImportMark;
import app.ari.api.feature.fileimport.domain.model.ImportStatus;
import app.ari.api.feature.fileimport.domain.model.MarkColor;
import app.ari.api.feature.fileimport.domain.model.ReadFailure;
import app.ari.api.feature.fileimport.domain.model.UploadSpace;
import app.ari.api.feature.fileimport.domain.port.MarkReader;
import app.ari.api.feature.fileimport.domain.repository.ImportRepository;
import app.ari.api.feature.fileimport.domain.service.ImportService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Storage keys (docs/database.md section 5): the upload at {@code imports/{userId}/{importId}}, a
 * mark's picture at {@code marks/{userId}/{markId}.png}. A file is written before the row that
 * points at it and deleted only after the row stops pointing at it, so no row points at nothing.
 */
@Service
public class ImportServiceImpl implements ImportService {

  // The Imports page lists this many at most.
  private static final int LIST_LIMIT = 200;
  private static final Duration LINK_LIFETIME = Duration.ofHours(1);

  private final ImportRepository imports;
  private final ObjectStorage storage;
  private final Map<FileKind, MarkReader> readers = new EnumMap<>(FileKind.class);
  private final Clock clock;

  public ImportServiceImpl(
      ImportRepository imports, ObjectStorage storage, List<MarkReader> readers, Clock clock) {
    this.imports = imports;
    this.storage = storage;
    for (MarkReader reader : readers) {
      this.readers.put(reader.kind(), reader);
    }
    this.clock = clock;
  }

  @Override
  public UploadSpace space(UUID userId) {
    return new UploadSpace(imports.usedBytes(userId));
  }

  @Override
  public UploadSpace lockSpace(UUID userId) {
    imports.lockUser(userId);
    return space(userId);
  }

  @Override
  public FileImport create(UUID userId, String fileName, FileKind kind, byte[] content) {
    UUID id = UUID.randomUUID();
    Instant now = now();
    String key = "imports/" + userId + "/" + id;
    storage.put(key, content, kind.contentType());
    OnRollback.run(() -> storage.deleteQuietly(key));
    FileImport created =
        new FileImport(
            id,
            userId,
            null,
            fileName,
            kind.contentType(),
            content.length,
            key,
            ImportStatus.QUEUED,
            1,
            null,
            null,
            null,
            null,
            now,
            now,
            0,
            0);
    imports.insert(created);
    return created;
  }

  @Override
  public FileImport createRead(UUID userId, String fileName, String contentType, long sizeBytes) {
    Instant now = now();
    FileImport created =
        new FileImport(
            UUID.randomUUID(),
            userId,
            null,
            fileName,
            contentType,
            sizeBytes,
            null,
            ImportStatus.READING,
            1,
            null,
            null,
            null,
            null,
            now,
            now,
            0,
            0);
    imports.insert(created);
    return created;
  }

  @Override
  public Optional<FileImport> find(UUID userId, UUID id) {
    return imports.findById(userId, id);
  }

  @Override
  public List<FileImport> list(UUID userId) {
    return imports.findAll(userId, LIST_LIMIT);
  }

  @Override
  public List<ImportMark> marks(UUID importId) {
    return imports.findMarks(importId);
  }

  @Override
  public Optional<FileImport> startReading(UUID id) {
    if (!imports.startReading(id, now())) {
      return Optional.empty();
    }
    return imports.findForReading(id);
  }

  @Override
  public FoundFile read(FileImport fileImport) {
    MarkReader reader =
        FileKind.ofContentType(fileImport.contentType())
            .map(readers::get)
            .orElseThrow(
                () ->
                    new FileReadException(
                        ReadFailure.UNREADABLE, "No reader for " + fileImport.contentType(), null));
    return reader.read(storage.get(fileImport.storageKey()));
  }

  @Override
  public void finishReading(FileImport fileImport, UUID sourceId, List<FoundMark> found) {
    Instant now = now();
    List<ImportMark> marks = new ArrayList<>();
    for (FoundMark mark : found) {
      UUID id = UUID.randomUUID();
      String imageKey = null;
      if (mark.picture() != null) {
        imageKey = "marks/" + fileImport.userId() + "/" + id + ".png";
        String key = imageKey;
        storage.put(key, mark.picture(), "image/png");
        OnRollback.run(() -> storage.deleteQuietly(key));
      }
      marks.add(
          new ImportMark(
              id,
              fileImport.id(),
              null,
              marks.size(),
              mark.type(),
              mark.color().color(),
              mark.color().fileColor(),
              mark.text(),
              mark.pageNumber(),
              mark.comment(),
              imageKey,
              mark.type().selectedAtFirst(),
              now));
    }
    if (!marks.isEmpty()) {
      imports.insertMarks(marks);
    }
    imports.finishReading(
        fileImport.id(),
        marks.isEmpty() ? null : sourceId,
        marks.isEmpty() ? ImportStatus.EMPTY : ImportStatus.READY,
        now);
    deleteFileAfterCommit(fileImport);
  }

  @Override
  public void fail(UUID id, ReadFailure failure) {
    imports.fail(id, failure.name(), now());
  }

  @Override
  public boolean requeue(FileImport fileImport) {
    return imports.requeue(fileImport.userId(), fileImport.id(), now());
  }

  @Override
  public void discard(FileImport fileImport, List<ImportMark> marks) {
    imports.deleteMarks(fileImport.id());
    imports.setStatus(fileImport.id(), ImportStatus.DISCARDED, now());
    for (ImportMark mark : marks) {
      if (mark.imageKey() != null) {
        AfterCommit.run(() -> storage.deleteQuietly(mark.imageKey()));
      }
    }
    deleteFileAfterCommit(fileImport);
  }

  @Override
  public void dismiss(FileImport fileImport) {
    imports.dismiss(fileImport.id(), now());
    deleteFileAfterCommit(fileImport);
  }

  @Override
  public void applyReview(
      FileImport fileImport,
      List<ImportMark> marks,
      Set<UUID> selected,
      Map<UUID, UUID> newSaves,
      MarkColor color) {
    for (ImportMark mark : marks) {
      boolean ticked = selected.contains(mark.id());
      UUID saveId = ticked ? newSaves.getOrDefault(mark.id(), mark.saveId()) : null;
      MarkColor markColor = color == null ? mark.color() : color;
      if (ticked != mark.selected()
          || markColor != mark.color()
          || !Objects.equals(saveId, mark.saveId())) {
        imports.updateMark(mark.id(), saveId, markColor, ticked);
      }
    }
    imports.markSaved(fileImport.id(), now());
  }

  @Override
  public Optional<String> pictureUrl(ImportMark mark) {
    return Optional.ofNullable(mark.imageKey()).map(key -> storage.signedUrl(key, LINK_LIFETIME));
  }

  @Override
  public byte[] picture(ImportMark mark) {
    return mark.imageKey() == null ? null : storage.get(mark.imageKey());
  }

  private void deleteFileAfterCommit(FileImport fileImport) {
    if (fileImport.storageKey() != null) {
      AfterCommit.run(() -> storage.deleteQuietly(fileImport.storageKey()));
    }
  }

  /** What the database stores: it keeps microseconds. */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
