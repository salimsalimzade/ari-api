package app.ari.api.feature.note.domain.service.impl;

import app.ari.api.common.search.SearchText;
import app.ari.api.feature.note.domain.model.Note;
import app.ari.api.feature.note.domain.model.NoteBlock;
import app.ari.api.feature.note.domain.model.NoteBlockKind;
import app.ari.api.feature.note.domain.model.NoteContent;
import app.ari.api.feature.note.domain.model.NoteDocument;
import app.ari.api.feature.note.domain.model.NoteQuote;
import app.ari.api.feature.note.domain.model.NoteSummary;
import app.ari.api.feature.note.domain.repository.NoteRepository;
import app.ari.api.feature.note.domain.service.NoteService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class NoteServiceImpl implements NoteService {

  private final NoteRepository notes;
  private final Clock clock;

  public NoteServiceImpl(NoteRepository notes, Clock clock) {
    this.notes = notes;
    this.clock = clock;
  }

  @Override
  public List<NoteSummary> list(UUID userId, String query) {
    String prefixQuery = SearchText.prefixQuery(query);
    String containsPattern = prefixQuery == null ? null : SearchText.containsPattern(query);
    Map<UUID, List<UUID>> quotes =
        notes.findQuotes(userId).stream()
            .collect(
                Collectors.groupingBy(
                    NoteQuote::noteId, Collectors.mapping(NoteQuote::saveId, Collectors.toList())));
    Set<UUID> written = new HashSet<>(notes.findWritten(userId));
    return notes.findAll(userId, prefixQuery, containsPattern).stream()
        .map(
            note -> {
              List<UUID> saveIds = quotes.getOrDefault(note.id(), List.of());
              boolean empty =
                  note.title().isEmpty() && saveIds.isEmpty() && !written.contains(note.id());
              return new NoteSummary(note, saveIds, empty);
            })
        .toList();
  }

  @Override
  public Optional<NoteDocument> find(UUID userId, UUID id) {
    return notes
        .findById(userId, id)
        .map(note -> new NoteDocument(note, notes.findBlocks(note.id())));
  }

  @Override
  public NoteDocument create(UUID userId, NoteContent content) {
    Instant now = now();
    Note note = new Note(UUID.randomUUID(), userId, content.title(), now, now);
    notes.insert(note);
    store(note.id(), content.blocks());
    notes.setBody(note.id(), bodyOf(content));
    return new NoteDocument(note, content.blocks());
  }

  @Override
  public Optional<NoteDocument> replace(UUID userId, UUID id, NoteContent content) {
    return notes
        .findById(userId, id)
        .map(
            found -> {
              Note note = new Note(id, userId, content.title(), found.createdAt(), now());
              notes.update(note);
              notes.deleteBlocks(id);
              store(id, content.blocks());
              notes.setBody(id, bodyOf(content));
              return new NoteDocument(note, content.blocks());
            });
  }

  @Override
  public boolean delete(UUID userId, UUID id) {
    return notes.delete(userId, id);
  }

  private void store(UUID noteId, List<NoteBlock> blocks) {
    if (!blocks.isEmpty()) {
      notes.insertBlocks(noteId, blocks);
    }
  }

  /** The paragraphs joined, for search. */
  private static String bodyOf(NoteContent content) {
    return content.blocks().stream()
        .filter(block -> block.kind() == NoteBlockKind.TEXT)
        .map(NoteBlock::text)
        .collect(Collectors.joining(" "));
  }

  /** What the database stores: it keeps microseconds. */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
