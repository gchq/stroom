package stroom.authentication.token;

import java.util.Optional;

public interface TokenService {
    SearchResponse search(SearchRequest searchRequest);

    Token create(CreateTokenRequest createTokenRequest);

    int deleteAll();

    int delete(int tokenId);

    int delete(String data);

    Optional<Token> read(String data);

    Optional<Token> read(int tokenId);

    int toggleEnabled(int tokenId, boolean isEnabled);

//    Optional<String> verifyToken(String token);

    String getPublicKey();

}
