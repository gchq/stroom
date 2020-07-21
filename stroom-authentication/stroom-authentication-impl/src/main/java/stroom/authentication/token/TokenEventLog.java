package stroom.authentication.token;

import stroom.util.shared.ResultPage;

public interface TokenEventLog {
    void search(SearchTokenRequest request, ResultPage<Token> response, Throwable ex);

    void create(CreateTokenRequest request, Token token, Throwable ex);

    void deleteAll(int count, Throwable ex);

    void delete(int tokenId, int count, Throwable ex);

    void delete(String data, int count, Throwable ex);

    void read(String data, Token token, Throwable ex);

    void read(int tokenId, Token token, Throwable ex);

    void toggleEnabled(int tokenId, boolean isEnabled, Throwable ex);

    void getPublicKey(Throwable ex);
}
