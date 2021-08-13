package stroom.security.identity.token;

import stroom.util.shared.ResultPage;

public interface ApiKeyEventLog {

    void search(SearchApiKeyRequest request, ResultPage<ApiKey> response, Throwable ex);

    void create(CreateApiKeyRequest request, ApiKey apiKey, Throwable ex);

    void deleteAll(int count, Throwable ex);

    void delete(int tokenId, int count, Throwable ex);

    void delete(String data, int count, Throwable ex);

    void read(String data, ApiKey apiKey, Throwable ex);

    void read(int tokenId, ApiKey apiKey, Throwable ex);

    void toggleEnabled(int tokenId, boolean isEnabled, Throwable ex);

    void getPublicKey(Throwable ex);
}
