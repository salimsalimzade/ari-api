package app.ari.api.feature.account.application.mapper;

import app.ari.api.feature.account.application.model.response.AuthTokens;
import app.ari.api.feature.account.application.model.response.ExtensionTokensResponse;
import app.ari.api.feature.account.application.model.response.MeResponse;
import app.ari.api.feature.account.domain.model.IssuedTokens;
import app.ari.api.feature.account.domain.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AccountMapper {

  /** {@code photoUrl} is the signed link to the user's photo, or null without one. */
  @Mapping(target = "photoUrl", source = "photoUrl")
  MeResponse toMeResponse(User user, String photoUrl);

  AuthTokens toAuthTokens(IssuedTokens tokens);

  ExtensionTokensResponse toExtensionTokens(IssuedTokens tokens);
}
