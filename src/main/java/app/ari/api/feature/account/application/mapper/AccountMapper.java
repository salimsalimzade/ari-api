package app.ari.api.feature.account.application.mapper;

import app.ari.api.feature.account.application.model.response.AuthTokens;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.domain.model.IssuedTokens;
import app.ari.api.feature.account.domain.model.User;
import org.mapstruct.Mapper;

@Mapper
public interface AccountMapper {

  MeResponse toMeResponse(User user);

  AuthTokens toAuthTokens(IssuedTokens tokens);
}
