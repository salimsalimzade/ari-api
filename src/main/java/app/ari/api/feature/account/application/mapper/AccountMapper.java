package app.ari.api.feature.account.application.mapper;

import app.ari.api.feature.account.application.model.response.AuthTokens;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.domain.model.IssuedTokens;
import app.ari.api.feature.account.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AccountMapper {

  @Mapping(target = "savesLimit", expression = "java(user.plan().savesLimit())")
  MeResponse toMeResponse(User user);

  AuthTokens toAuthTokens(IssuedTokens tokens);
}
