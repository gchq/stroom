package stroom.authentication.token;

import stroom.authentication.account.Account;
import stroom.authentication.config.TokenConfig;
import stroom.util.shared.ResultPage;

import java.util.Optional;

public interface TokenService {
    ResultPage<Token> list();

    ResultPage<Token> search(SearchTokenRequest request);

    Token create(CreateTokenRequest createTokenRequest);

    Token createResetEmailToken(Account account, String clientId);

    Optional<Token> read(String data);

    Optional<Token> read(int tokenId);

    int toggleEnabled(int tokenId, boolean isEnabled);

    int delete(int tokenId);

    int delete(String data);

    int deleteAll();

    String getPublicKey();

    TokenConfig fetchTokenConfig();
}
