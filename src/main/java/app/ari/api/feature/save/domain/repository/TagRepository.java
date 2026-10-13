package app.ari.api.feature.save.domain.repository;

import app.ari.api.feature.save.domain.model.SaveTag;
import app.ari.api.feature.save.domain.model.Tag;
import app.ari.api.feature.save.domain.model.TagCount;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Stores tags and which saves have them. */
public interface TagRepository {

  /**
   * Adds the tags the user doesn't have yet (names compared in any case) and returns the stored row
   * of every one, so a tag that already existed comes back with its own id and spelling. The names
   * must differ in lowercase.
   */
  List<Tag> upsertAll(UUID userId, List<Tag> tags);

  void link(UUID saveId, Collection<UUID> tagIds);

  void unlinkAll(UUID saveId);

  /** The tags of these saves, alphabetical. */
  List<SaveTag> findBySaves(Collection<UUID> saveIds);

  /** Saves per tag over the whole Library, most used first. */
  List<TagCount> countByTag(UUID userId);
}
