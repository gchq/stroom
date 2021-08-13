package stroom.security.identity.token;

import java.util.Optional;

public interface ApiKeyService {

    ApiKeyResultPage list();

    ApiKeyResultPage search(SearchApiKeyRequest request);

    ApiKey create(CreateApiKeyRequest createApiKeyRequest);

    Optional<ApiKey> read(String data);

    Optional<ApiKey> read(int id);

    int toggleEnabled(int id, boolean isEnabled);

    int delete(int id);

    int delete(String data);

    int deleteAll();
}
