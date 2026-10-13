package app.ari.api.feature.outbox.infrastructure;

import app.ari.api.feature.outbox.domain.repository.OutboxRepository;
import org.apache.ibatis.annotations.Mapper;

/**
 * PostgreSQL adapter for {@link OutboxRepository}; SQL in persistence/OutboxMyBatisRepository.xml.
 */
@Mapper
public interface OutboxMyBatisRepository extends OutboxRepository {}
