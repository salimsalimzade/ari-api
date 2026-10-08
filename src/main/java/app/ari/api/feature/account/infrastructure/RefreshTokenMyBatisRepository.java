package app.ari.api.feature.account.infrastructure;

import app.ari.api.feature.account.domain.repository.RefreshTokenRepository;
import org.apache.ibatis.annotations.Mapper;

/**
 * PostgreSQL adapter for {@link RefreshTokenRepository}; SQL in
 * persistence/RefreshTokenMyBatisRepository.xml.
 */
@Mapper
public interface RefreshTokenMyBatisRepository extends RefreshTokenRepository {}
