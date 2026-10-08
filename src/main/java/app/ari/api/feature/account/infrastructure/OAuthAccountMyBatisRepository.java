package app.ari.api.feature.account.infrastructure;

import app.ari.api.feature.account.domain.repository.OAuthAccountRepository;
import org.apache.ibatis.annotations.Mapper;

/**
 * PostgreSQL adapter for {@link OAuthAccountRepository}; SQL in
 * persistence/OAuthAccountMyBatisRepository.xml.
 */
@Mapper
public interface OAuthAccountMyBatisRepository extends OAuthAccountRepository {}
