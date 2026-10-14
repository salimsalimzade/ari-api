package app.ari.api.feature.save.domain.service.impl;

import app.ari.api.common.client.ObjectStorage;
import app.ari.api.feature.save.domain.model.ClipImage;
import app.ari.api.feature.save.domain.model.HighlightColor;
import app.ari.api.feature.save.domain.model.NewSave;
import app.ari.api.feature.save.domain.model.Save;
import app.ari.api.feature.save.domain.model.SaveChange;
import app.ari.api.feature.save.domain.model.SaveCursor;
import app.ari.api.feature.save.domain.model.SaveFacets;
import app.ari.api.feature.save.domain.model.SaveFilter;
import app.ari.api.feature.save.domain.model.SaveKind;
import app.ari.api.feature.save.domain.model.SavePage;
import app.ari.api.feature.save.domain.model.SaveTag;
import app.ari.api.feature.save.domain.model.Tag;
import app.ari.api.feature.save.domain.model.TaggedSave;
import app.ari.api.feature.save.domain.repository.SaveRepository;
import app.ari.api.feature.save.domain.repository.TagRepository;
import app.ari.api.feature.save.domain.service.SaveService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * An image is stored before the row that points at it, and the file of a deleted save is only
 * removed after the deletion committed (see {@link #discardImage(Save)}), so a row never points at
 * a missing file.
 */
@Service
public class SaveServiceImpl implements SaveService {

  private static final Duration LINK_LIFETIME = Duration.ofHours(1);

  private final SaveRepository saves;
  private final TagRepository tags;
  private final ObjectStorage storage;
  private final Clock clock;

  public SaveServiceImpl(
      SaveRepository saves, TagRepository tags, ObjectStorage storage, Clock clock) {
    this.saves = saves;
    this.tags = tags;
    this.storage = storage;
    this.clock = clock;
  }

  @Override
  public TaggedSave create(UUID userId, UUID sourceId, NewSave newSave, ClipImage image) {
    UUID id = UUID.randomUUID();
    Instant now = now();
    String imageKey =
        image == null ? null : "saves/" + userId + "/" + id + "." + image.format().extension();
    Save save =
        new Save(
            id,
            userId,
            sourceId,
            newSave.kind(),
            newSave.color(),
            newSave.clipType(),
            newSave.text(),
            newSave.contentMd(),
            imageKey,
            newSave.comment(),
            newSave.section(),
            null,
            now,
            now);
    if (image != null) {
      storage.put(imageKey, image.bytes(), image.format().contentType());
    }
    try {
      saves.insert(save);
      return new TaggedSave(save, tag(userId, id, newSave.tags(), now));
    } catch (RuntimeException e) {
      if (imageKey != null) {
        storage.deleteQuietly(imageKey);
      }
      throw e;
    }
  }

  @Override
  public Optional<Save> findById(UUID userId, UUID id) {
    return saves.findById(userId, id);
  }

  @Override
  public Optional<TaggedSave> find(UUID userId, UUID id) {
    return saves
        .findById(userId, id)
        .map(
            save ->
                new TaggedSave(
                    save, tagNames(List.of(save.id())).getOrDefault(save.id(), List.of())));
  }

  @Override
  public TaggedSave update(Save save, SaveChange change) {
    Instant now = now();
    Save changed =
        new Save(
            save.id(),
            save.userId(),
            save.sourceId(),
            save.kind(),
            change.color() != null ? change.color() : save.color(),
            save.clipType(),
            save.text(),
            save.contentMd(),
            save.imageKey(),
            change.comment() != null ? change.comment() : save.comment(),
            save.section(),
            save.pageNumber(),
            save.createdAt(),
            now);
    saves.update(changed);
    if (change.tags() == null) {
      return new TaggedSave(
          changed, tagNames(List.of(save.id())).getOrDefault(save.id(), List.of()));
    }
    tags.unlinkAll(save.id());
    return new TaggedSave(changed, tag(save.userId(), save.id(), change.tags(), now));
  }

  @Override
  public SavePage page(UUID userId, SaveFilter filter, SaveCursor after, int limit) {
    // One more than asked for tells whether there is a next page.
    List<Save> rows =
        saves.findPage(
            userId,
            filter,
            after == null ? null : after.createdAt(),
            after == null ? null : after.id(),
            limit + 1);
    boolean more = rows.size() > limit;
    List<Save> shown = more ? rows.subList(0, limit) : rows;
    Map<UUID, List<String>> names = tagNames(shown.stream().map(Save::id).toList());
    List<TaggedSave> items =
        shown.stream()
            .map(save -> new TaggedSave(save, names.getOrDefault(save.id(), List.of())))
            .toList();
    SaveCursor next =
        more ? new SaveCursor(shown.getLast().createdAt(), shown.getLast().id()) : null;
    return new SavePage(items, next);
  }

  @Override
  public SaveFacets facets(UUID userId, SaveFilter filter) {
    Map<SaveKind, Integer> kinds = new EnumMap<>(SaveKind.class);
    for (SaveKind kind : SaveKind.values()) {
      kinds.put(kind, 0);
    }
    saves
        .countByKind(userId, filter.withoutKind())
        .forEach(row -> kinds.put(row.kind(), row.count()));
    Map<HighlightColor, Integer> colors = new EnumMap<>(HighlightColor.class);
    for (HighlightColor color : HighlightColor.values()) {
      colors.put(color, 0);
    }
    saves.countByColor(userId).forEach(row -> colors.put(row.color(), row.count()));
    return new SaveFacets(kinds, tags.countByTag(userId), colors, saves.countBySource(userId));
  }

  @Override
  public Optional<Save> delete(UUID userId, UUID id) {
    Optional<Save> save = saves.findById(userId, id);
    return save.isPresent() && saves.delete(userId, id) ? save : Optional.empty();
  }

  @Override
  public void discardImage(Save save) {
    if (save.imageKey() != null) {
      storage.deleteQuietly(save.imageKey());
    }
  }

  @Override
  public Optional<String> imageUrl(Save save) {
    return Optional.ofNullable(save.imageKey()).map(key -> storage.signedUrl(key, LINK_LIFETIME));
  }

  /** Puts the tags on the save, making the ones the user doesn't have yet; returns their names. */
  private List<String> tag(UUID userId, UUID saveId, List<String> names, Instant now) {
    if (names.isEmpty()) {
      return List.of();
    }
    List<Tag> wanted =
        names.stream().map(name -> new Tag(UUID.randomUUID(), userId, name, now)).toList();
    List<Tag> stored = tags.upsertAll(userId, wanted);
    tags.link(saveId, stored.stream().map(Tag::id).toList());
    return stored.stream()
        .map(Tag::name)
        .sorted(Comparator.comparing(name -> name.toLowerCase(Locale.ROOT)))
        .toList();
  }

  private Map<UUID, List<String>> tagNames(List<UUID> saveIds) {
    if (saveIds.isEmpty()) {
      return Map.of();
    }
    return tags.findBySaves(saveIds).stream()
        .collect(
            Collectors.groupingBy(
                SaveTag::saveId, Collectors.mapping(SaveTag::name, Collectors.toList())));
  }

  /** What the database stores: it keeps microseconds. */
  private Instant now() {
    return clock.instant().truncatedTo(ChronoUnit.MICROS);
  }
}
