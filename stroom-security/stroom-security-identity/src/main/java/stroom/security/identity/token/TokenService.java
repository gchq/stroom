package stroom.security.identity.token;

import stroom.security.identity.account.Account;
import stroom.security.identity.config.TokenConfig;

import java.util.Optional;

public interface TokenService {

    TokenResultPage list();

    TokenResultPage search(SearchTokenRequest request);

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
