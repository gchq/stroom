package stroom.security.impl.apikey;

import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.apikey.ApiKeyService.DuplicateApiKeyException;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.util.shared.ResultPage;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApiKeyDao {

    ResultPage<HashedApiKey> find(final FindApiKeyCriteria criteria);

    /**
     * Fetch valid API Keys by their prefix. Keys will all be enabled and not expired.
     * Chance of multiple keys for a given prefix is low, ~1:1,000,000 odds, but possible.
     */
    List<HashedApiKey> fetchValidApiKeysByPrefix(final String prefix);

    HashedApiKey create(final CreateHashedApiKeyRequest createHashedApiKeyRequest,
                        final HashedApiKeyParts hashedApiKeyParts) throws DuplicateApiKeyException;

    Optional<HashedApiKey> fetch(final int id);

    HashedApiKey update(final HashedApiKey apiKey);

    boolean delete(final int id);

    int delete(final Collection<Integer> ids);
}
