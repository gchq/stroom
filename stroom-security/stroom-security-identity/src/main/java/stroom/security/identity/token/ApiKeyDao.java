package stroom.security.identity.token;

import stroom.security.identity.account.Account;

import java.util.List;
import java.util.Optional;

public interface ApiKeyDao {

    ApiKeyResultPage list();

    ApiKeyResultPage search(SearchApiKeyRequest request);

    ApiKey create(int accountId, ApiKey apiKey);

    Optional<ApiKey> readById(int tokenId);

    Optional<ApiKey> readByToken(String token);

    List<ApiKey> getTokensForAccount(int accountId);

    int enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount);

    int deleteAllTokensExceptAdmins();

    int deleteTokenById(int tokenId);

    int deleteTokenByTokenString(String token);
}
