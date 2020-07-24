package stroom.authentication.token;

import stroom.authentication.account.Account;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

public interface TokenDao {
    ResultPage<Token> list();

    ResultPage<Token> search(SearchTokenRequest request);

    Token create(int accountId, Token token);

    Optional<Token> readById(int tokenId);

    Optional<Token> readByToken(String token);

    List<Token> getTokensForAccount(int accountId);

    int enableOrDisableToken(int tokenId, boolean enabled, Account updatingAccount);

    int deleteAllTokensExceptAdmins();

    int deleteTokenById(int tokenId);

    int deleteTokenByTokenString(String token);
}
