package app.ari.api.feature.note.application.mapper;

import app.ari.api.feature.note.application.model.request.NoteRequest;
import app.ari.api.feature.note.application.model.response.NoteBlockResponse;
import app.ari.api.feature.note.application.model.response.NoteResponse;
import app.ari.api.feature.note.application.model.response.NoteSummaryResponse;
import app.ari.api.feature.note.domain.model.Note;
import app.ari.api.feature.note.domain.model.NoteBlock;
import app.ari.api.feature.note.domain.model.NoteBlockKind;
import app.ari.api.feature.note.domain.model.NoteContent;
import app.ari.api.feature.note.domain.model.NoteDocument;
import app.ari.api.feature.note.domain.model.NoteSummary;
import app.ari.api.feature.save.application.model.response.SaveResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface NoteMapper {

  // By hand: MapStruct takes NoteContent.quotingOnly for a property to fill.
  default NoteContent toContent(NoteRequest request) {
    return new NoteContent(
        request.title(),
        request.blocks().stream()
            .map(block -> new NoteBlock(block.kind(), block.text(), block.saveId()))
            .toList());
  }

  @Mapping(target = ".", source = "note")
  NoteSummaryResponse toSummaryResponse(NoteSummary summary);

  /** {@code saves} holds the quoted saves by id; a quote of one that isn't there is left out. */
  default NoteResponse toResponse(NoteDocument document, Map<UUID, SaveResponse> saves) {
    List<NoteBlockResponse> blocks =
        document.blocks().stream()
            .filter(
                block -> block.kind() == NoteBlockKind.TEXT || saves.containsKey(block.saveId()))
            .map(
                block ->
                    block.kind() == NoteBlockKind.TEXT
                        ? new NoteBlockResponse(NoteBlockKind.TEXT, block.text(), null)
                        : new NoteBlockResponse(
                            NoteBlockKind.QUOTE, null, saves.get(block.saveId())))
            .toList();
    Note note = document.note();
    return new NoteResponse(note.id(), note.title(), blocks, note.createdAt(), note.updatedAt());
  }
}
