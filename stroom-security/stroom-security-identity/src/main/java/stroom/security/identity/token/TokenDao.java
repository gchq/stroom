package stroom.security.identity.token;

import stroom.security.identity.account.Account;

import java.util.List;
import java.util.Optional;

public interface TokenDao {
    TokenResultPage list();

    TokenResultPage search(SearchTokenRequest request);

    Token create(int accountId, Token token);

    Optional<Token> readById(int tokenId);

    Optional<Token> readByToken(String token);

    List<Token> getTokensForAccount(int accountId);

    int enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount);

    int deleteAllTokensExceptAdmins();

    int deleteTokenById(int tokenId);

    int deleteTokenByTokenString(String token);
}
