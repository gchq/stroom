package stroom.authentication.token;

import stroom.authentication.account.Account;
import stroom.authentication.exceptions.NoSuchUserException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TokenDao {
    Token create(int accountId, Token token);

    Optional<Token> readById(int tokenId);

    Optional<Token> readByToken(String token);

    List<Token> getTokensForAccount(int accountId);

    int enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount);

    int deleteAllTokensExceptAdmins();

    int deleteTokenById(int tokenId);

    int deleteTokenByTokenString(String token);

    SearchResponse searchTokens(SearchRequest searchRequest);
}
