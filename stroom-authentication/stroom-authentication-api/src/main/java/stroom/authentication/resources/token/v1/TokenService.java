package stroom.authentication.resources.token.v1;

import java.util.Optional;

public interface TokenService {
    SearchResponse search(SearchRequest searchRequest);

    Token create(CreateTokenRequest createTokenRequest);

    void deleteAll();

    void delete(int tokenId);

    void delete(String token);

    Optional<Token> read(String token);

    Optional<Token> read(int tokenId);

    void toggleEnabled(int tokenId, boolean isEnabled);

    Optional<String> verifyToken(String token);

    String getPublicKey();

}
