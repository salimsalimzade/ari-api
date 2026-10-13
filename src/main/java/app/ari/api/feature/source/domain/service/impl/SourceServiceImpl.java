package app.ari.api.feature.source.domain.service.impl;

import app.ari.api.feature.source.domain.model.Source;
import app.ari.api.feature.source.domain.model.SourceKind;
import app.ari.api.feature.source.domain.model.SourceUrl;
import app.ari.api.feature.source.domain.repository.SourceRepository;
import app.ari.api.feature.source.domain.service.SourceService;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SourceServiceImpl implements SourceService {

  private final SourceRepository sources;
  private final Clock clock;

  public SourceServiceImpl(SourceRepository sources, Clock clock) {
    this.sources = sources;
    this.clock = clock;
  }

  @Override
  public Optional<Source> saveWebsite(
      UUID userId, String address, String name, String title, String author) {
    return SourceUrl.normalize(address)
        .map(
            url -> {
              Instant now = clock.instant();
              String writer = author == null || author.isBlank() ? null : author.strip();
              return sources.upsertWebsite(
                  new Source(
                      UUID.randomUUID(),
                      userId,
                      SourceKind.WEBSITE,
                      name.strip(),
                      title.strip(),
                      writer,
                      url,
                      now,
                      now));
            });
  }

  @Override
  public List<Source> findAllByIds(UUID userId, Collection<UUID> ids) {
    return ids.isEmpty() ? List.of() : sources.findAllByIds(userId, ids);
  }
}
