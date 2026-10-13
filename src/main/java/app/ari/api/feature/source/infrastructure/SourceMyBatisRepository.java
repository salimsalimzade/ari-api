package app.ari.api.feature.source.infrastructure;

import app.ari.api.feature.source.domain.repository.SourceRepository;
import org.apache.ibatis.annotations.Mapper;

/**
 * PostgreSQL adapter for {@link SourceRepository}; SQL in persistence/SourceMyBatisRepository.xml.
 */
@Mapper
public interface SourceMyBatisRepository extends SourceRepository {}
