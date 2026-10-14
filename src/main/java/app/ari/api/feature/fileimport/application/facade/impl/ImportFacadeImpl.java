package app.ari.api.feature.fileimport.application.facade.impl;

import app.ari.api.common.client.Queues;
import app.ari.api.common.client.UnprocessableMessageException;
import app.ari.api.feature.fileimport.application.exception.badrequest.NoSpaceLeftException;
import app.ari.api.feature.fileimport.application.exception.badrequest.UnsupportedFileException;
import app.ari.api.feature.fileimport.application.exception.conflict.ImportStateException;
import app.ari.api.feature.fileimport.application.exception.notfound.ImportNotFoundException;
import app.ari.api.feature.fileimport.application.exception.payloadtoolarge.ImportTooLargeException;
import app.ari.api.feature.fileimport.application.facade.ImportFacade;
import app.ari.api.feature.fileimport.application.mapper.ImportMapper;
import app.ari.api.feature.fileimport.application.model.message.ImportRequested;
import app.ari.api.feature.fileimport.application.model.request.ReviewRequest;
import app.ari.api.feature.fileimport.application.model.response.ImportMarkResponse;
import app.ari.api.feature.fileimport.application.model.response.ImportResponse;
import app.ari.api.feature.fileimport.application.model.response.ImportsResponse;
import app.ari.api.feature.fileimport.application.model.response.ReviewResponse;
import app.ari.api.feature.fileimport.domain.model.FileImport;
import app.ari.api.feature.fileimport.domain.model.FileKind;
import app.ari.api.feature.fileimport.domain.model.FileReadException;
import app.ari.api.feature.fileimport.domain.model.FoundFile;
import app.ari.api.feature.fileimport.domain.model.ImportMark;
import app.ari.api.feature.fileimport.domain.model.ImportStatus;
import app.ari.api.feature.fileimport.domain.model.MarkColor;
import app.ari.api.feature.fileimport.domain.model.MarkType;
import app.ari.api.feature.fileimport.domain.model.ReadFailure;
import app.ari.api.feature.fileimport.domain.model.UploadSpace;
import app.ari.api.feature.fileimport.domain.service.ImportService;
import app.ari.api.feature.outbox.application.facade.OutboxFacade;
import app.ari.api.feature.outbox.application.model.request.NewOutboxEvent;
import app.ari.api.feature.save.application.facade.SaveFacade;
import app.ari.api.feature.save.application.model.request.ImportedSaveRequest;
import app.ari.api.feature.source.application.facade.SourceFacade;
import app.ari.api.feature.source.application.model.response.SourceResponse;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An upload stores its file and writes its import and the outbox event in one transaction, so a job
 * is never lost; the consumer reads the file outside any transaction and keeps what it found in
 * one. A review's saves go through the save feature, so the saves count stays right.
 */
@Service
public class ImportFacadeImpl implements ImportFacade {

  /** The consumer's name in processed_events. */
  private static final String CONSUMER = Queues.IMPORT_PROCESS;

  private static final int FILE_NAME_LENGTH = 255;

  private final ImportService importService;
  private final SourceFacade sources;
  private final SaveFacade saves;
  private final OutboxFacade outbox;
  private final ImportMapper mapper;
  private final TransactionTemplate transaction;

  public ImportFacadeImpl(
      ImportService importService,
      SourceFacade sources,
      SaveFacade saves,
      OutboxFacade outbox,
      ImportMapper mapper,
      TransactionTemplate transaction) {
    this.importService = importService;
    this.sources = sources;
    this.saves = saves;
    this.outbox = outbox;
    this.mapper = mapper;
    this.transaction = transaction;
  }

  @Override
  public ImportsResponse list(UUID userId) {
    List<FileImport> found = importService.list(userId);
    Map<UUID, SourceResponse> fileSources =
        sources.findAll(
            userId,
            found.stream()
                .map(FileImport::sourceId)
                .filter(id -> id != null)
                .collect(Collectors.toSet()));
    return new ImportsResponse(
        found.stream()
            .map(item -> mapper.toResponse(item, sourceOf(item, fileSources), null))
            .toList(),
        mapper.toSpaceResponse(importService.space(userId)));
  }

  @Override
  public ImportResponse get(UUID userId, UUID id) {
    FileImport found = find(userId, id);
    List<ImportMarkResponse> marks =
        found.status() == ImportStatus.READY || found.status() == ImportStatus.SAVED
            ? importService.marks(id).stream()
                .map(
                    mark ->
                        mapper.toMarkResponse(mark, importService.pictureUrl(mark).orElse(null)))
                .toList()
            : null;
    Map<UUID, SourceResponse> fileSources =
        found.sourceId() == null ? Map.of() : sources.findAll(userId, Set.of(found.sourceId()));
    return mapper.toResponse(found, sourceOf(found, fileSources), marks);
  }

  @Override
  @Transactional
  public ImportResponse upload(UUID userId, String fileName, byte[] content) {
    // Bigger than the whole space, it can never fit.
    if (content.length > UploadSpace.LIMIT_BYTES) {
      throw new ImportTooLargeException();
    }
    FileKind kind = FileKind.of(content).orElseThrow(UnsupportedFileException::new);
    if (!importService.lockSpace(userId).fits(content.length)) {
      throw new NoSpaceLeftException();
    }
    FileImport created = importService.create(userId, nameOf(fileName, kind), kind, content);
    queue(created.id());
    return mapper.toResponse(created, null, null);
  }

  @Override
  @Transactional
  public ReviewResponse review(UUID userId, UUID id, ReviewRequest request) {
    FileImport found = find(userId, id);
    if (found.status() != ImportStatus.READY && found.status() != ImportStatus.SAVED) {
      throw new ImportStateException("Only a file that has been read can be reviewed");
    }
    List<ImportMark> marks = importService.marks(id);
    Set<UUID> selected = new HashSet<>(request.selectedMarkIds());
    // A file's source only goes away with its import's discard; make one again just in case.
    UUID sourceId =
        found.sourceId() != null
            ? found.sourceId()
            : sources.createFile(userId, found.fileName(), null, null).id();
    SourceResponse source =
        sources
            .updateFile(userId, sourceId, request.title(), request.author())
            .orElseThrow(ImportNotFoundException::new);

    int removed = 0;
    for (ImportMark mark : marks) {
      if (mark.saveId() != null && !selected.contains(mark.id())) {
        saves.delete(userId, mark.saveId());
        removed++;
      }
    }
    List<ImportMark> adding =
        marks.stream()
            .filter(mark -> mark.saveId() == null && selected.contains(mark.id()))
            .toList();
    List<String> tags =
        request.tag() == null || request.tag().isBlank() ? List.of() : List.of(request.tag());
    List<UUID> created =
        saves.createImported(
            userId,
            sourceId,
            adding.stream().map(mark -> toSave(mark, request.color(), source, tags)).toList());
    Map<UUID, UUID> newSaves = new HashMap<>();
    for (int i = 0; i < adding.size(); i++) {
      newSaves.put(adding.get(i).id(), created.get(i));
    }
    importService.applyReview(found, marks, selected, newSaves, request.color());
    return new ReviewResponse(adding.size(), removed, sourceId);
  }

  @Override
  @Transactional
  public ImportResponse retry(UUID userId, UUID id) {
    FileImport found = find(userId, id);
    if (!importService.requeue(found)) {
      throw new ImportStateException("Only a file that failed to read can be read again");
    }
    queue(id);
    return mapper.toResponse(find(userId, id), null, null);
  }

  @Override
  @Transactional
  public void discard(UUID userId, UUID id) {
    FileImport found = find(userId, id);
    if (found.status() != ImportStatus.READY) {
      throw new ImportStateException("Only a file waiting for its review can be discarded");
    }
    importService.discard(found, importService.marks(id));
    if (found.sourceId() != null) {
      sources.deleteFileIfUnused(userId, found.sourceId());
    }
  }

  @Override
  @Transactional
  public void dismiss(UUID userId, UUID id) {
    FileImport found = find(userId, id);
    if (found.status() != ImportStatus.FAILED
        && found.status() != ImportStatus.EMPTY
        && found.status() != ImportStatus.DISCARDED) {
      throw new ImportStateException("Only a failed, empty or discarded file can be dismissed");
    }
    importService.dismiss(found);
  }

  @Override
  public void read(String eventId, UUID importId) {
    if (outbox.wasProcessed(CONSUMER, eventId)) {
      return;
    }
    // Discarded, dismissed or read already: nothing to do.
    Optional<FileImport> started =
        transaction.execute(status -> importService.startReading(importId));
    if (started == null || started.isEmpty()) {
      return;
    }
    FileImport reading = started.get();
    FoundFile found;
    try {
      found = importService.read(reading);
    } catch (FileReadException e) {
      throw new UnprocessableMessageException(e.failure().name(), e);
    }
    transaction.executeWithoutResult(
        status -> {
          if (!outbox.markProcessed(CONSUMER, eventId)) {
            return;
          }
          UUID sourceId =
              found.marks().isEmpty()
                  ? null
                  : sources
                      .createFile(
                          reading.userId(), reading.fileName(), found.title(), found.author())
                      .id();
          importService.finishReading(reading, sourceId, found.marks());
        });
  }

  @Override
  public void giveUp(UUID importId, RuntimeException failure) {
    ReadFailure reason =
        failure.getCause() instanceof FileReadException unreadable
            ? unreadable.failure()
            : ReadFailure.READ_FAILED;
    transaction.executeWithoutResult(status -> importService.fail(importId, reason));
  }

  private FileImport find(UUID userId, UUID id) {
    return importService.find(userId, id).orElseThrow(ImportNotFoundException::new);
  }

  private void queue(UUID importId) {
    outbox.add(
        new NewOutboxEvent(
            "IMPORT",
            importId,
            "ImportRequested",
            Queues.IMPORT_PROCESS,
            new ImportRequested(importId)));
  }

  /**
   * A mark as a save: its words with its color (or the review's), or, for a handwritten mark with a
   * picture, an image clip with the file's title as its caption when it has no words.
   */
  private ImportedSaveRequest toSave(
      ImportMark mark, MarkColor color, SourceResponse source, List<String> tags) {
    byte[] picture = mark.type() == MarkType.HANDWRITTEN ? importService.picture(mark) : null;
    String text = mark.text().isBlank() ? source.title() : mark.text();
    return new ImportedSaveRequest(
        (color == null ? mark.color() : color).name(),
        text,
        mark.comment(),
        mark.pageNumber(),
        tags,
        picture);
  }

  private static SourceResponse sourceOf(FileImport item, Map<UUID, SourceResponse> fileSources) {
    return item.sourceId() == null ? null : fileSources.get(item.sourceId());
  }

  /** The file's own name, without folders, fit for its column; or one made from its kind. */
  private static String nameOf(String fileName, FileKind kind) {
    String name = fileName == null ? "" : fileName.replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1).strip();
    if (name.isEmpty()) {
      name = "file." + kind.name().toLowerCase(Locale.ROOT);
    }
    return name.length() <= FILE_NAME_LENGTH ? name : name.substring(0, FILE_NAME_LENGTH);
  }
}
