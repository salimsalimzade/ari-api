package app.ari.api.feature.save.domain.repository;

import app.ari.api.feature.save.domain.model.ColorCount;
import app.ari.api.feature.save.domain.model.KindCount;
import app.ari.api.feature.save.domain.model.Save;
import app.ari.api.feature.save.domain.model.SaveCursor;
import app.ari.api.feature.save.domain.model.SaveFilter;
import app.ari.api.feature.save.domain.model.SaveOrder;
import app.ari.api.feature.save.domain.model.SourceCount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores saves. Every query is limited to one user's saves. */
public interface SaveRepository {

  void insert(Save save);

  /** Empty when there is no such save, or it is someone else's. */
  Optional<Save> findById(UUID userId, UUID id);

  /** The user's own saves among {@code ids}; unknown ids and someone else's are left out. */
  List<Save> findAllByIds(UUID userId, Collection<UUID> ids);

  /**
   * Up to {@code limit} saves that match {@code filter}, in {@code order}. When {@code after} is
   * given, only the saves past it: keyset paging on (created_at, id) newest first, or on (page
   * number, created_at, id) in the page order, where a save without a page number counts as page 0.
   */
  List<Save> findPage(UUID userId, SaveFilter filter, SaveOrder order, SaveCursor after, int limit);

  /** Saves the color, comment and update time. */
  void update(Save save);

  /** False when there was nothing to delete. Its tag links go with it. */
  boolean delete(UUID userId, UUID id);

  /** Saves per type among those that match {@code filter}, which should have no kind. */
  List<KindCount> countByKind(UUID userId, SaveFilter filter);

  /** Saves per color over the whole Library; colors with none are left out. */
  List<ColorCount> countByColor(UUID userId);

  List<SourceCount> countBySource(UUID userId);
}
