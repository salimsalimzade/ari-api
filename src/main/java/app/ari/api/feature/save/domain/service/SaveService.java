package app.ari.api.feature.save.domain.service;

import app.ari.api.feature.save.domain.model.ClipImage;
import app.ari.api.feature.save.domain.model.NewSave;
import app.ari.api.feature.save.domain.model.Save;
import app.ari.api.feature.save.domain.model.SaveChange;
import app.ari.api.feature.save.domain.model.SaveCursor;
import app.ari.api.feature.save.domain.model.SaveFacets;
import app.ari.api.feature.save.domain.model.SaveFilter;
import app.ari.api.feature.save.domain.model.SavePage;
import app.ari.api.feature.save.domain.model.TaggedSave;
import java.util.Optional;
import java.util.UUID;

/** The Library: what is saved, and how it is found, changed and removed. */
public interface SaveService {

  /**
   * Saves a highlight or clip from the source. An image is stored first and removed again if the
   * save fails. The caller has checked {@link NewSave#violation(boolean)}.
   */
  TaggedSave create(UUID userId, UUID sourceId, NewSave newSave, ClipImage image);

  /** Empty when there is no such save, or it is someone else's. */
  Optional<Save> findById(UUID userId, UUID id);

  /** The save with its tags; empty when there is no such save, or it is someone else's. */
  Optional<TaggedSave> find(UUID userId, UUID id);

  /** Applies the edit; the caller has checked {@link SaveChange#violationFor(Save)}. */
  TaggedSave update(Save save, SaveChange change);

  /** The saves that match {@code filter}, newest first, after the cursor when given. */
  SavePage page(UUID userId, SaveFilter filter, SaveCursor after, int limit);

  SaveFacets facets(UUID userId, SaveFilter filter);

  /**
   * Deletes a save and returns it; empty when there was none. Its image file is not touched: call
   * {@link #discardImage(Save)} once the deletion is committed.
   */
  Optional<Save> delete(UUID userId, UUID id);

  /** Deletes a deleted save's image file, if it had one; a failure only leaves it orphaned. */
  void discardImage(Save save);

  /** A link that shows an image clip's picture for an hour; empty for anything else. */
  Optional<String> imageUrl(Save save);
}
