package stroom.authentication.token;

import stroom.authentication.account.Account;

import java.util.Optional;

public interface TokenService {
    SearchResponse search(SearchRequest searchRequest);

    Token create(CreateTokenRequest createTokenRequest);

    Token createResetEmailToken(Account account, String clientId);

    int deleteAll();

    int delete(int tokenId);

    int delete(String data);

    Optional<Token> read(String data);

    Optional<Token> read(int tokenId);

    int toggleEnabled(int tokenId, boolean isEnabled);

//    Optional<String> verifyToken(String token);

    String getPublicKey();
}
