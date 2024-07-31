package stroom.security.impl.apikey;

import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.apikey.ApiKeyService.DuplicateHashException;
import stroom.security.impl.apikey.ApiKeyService.DuplicatePrefixException;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.util.NullSafe;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.shared.UserRef;

import java.util.Collection;
import java.util.Optional;

public interface ApiKeyDao {

    FilterFieldMappers<HashedApiKey> FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_OWNER_DISPLAY_NAME, (HashedApiKey apiKey) ->
                    NullSafe.get(apiKey.getOwner(), UserRef::toDisplayString)),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_NAME, HashedApiKey::getName),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_PREFIX, HashedApiKey::getApiKeyPrefix),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_COMMENTS, HashedApiKey::getComments),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_ENABLED,
                    apiKey -> apiKey.getEnabled()
                            ? "enabled"
                            : "disabled"));

    ApiKeyResultPage find(final FindApiKeyCriteria criteria);

    /**
     * Verify an API key, ensuring it exists and is enabled, returning the stroom user
     * UUID of the verified user.
     *
     * @param apiKeyHash The API key hash to verify.
     * @return The stroom user UUID of the verified user.
     * If the API key doesn't exist or is disabled/expired, an empty {@link Optional}
     * will be returned.
     */
    Optional<String> fetchVerifiedUserUuid(final String apiKeyHash);

    /**
     * Fetch an API key by its hash if it is enabled and not expired.
     */
    Optional<HashedApiKey> fetchValidApiKeyByHash(String hash);

    HashedApiKey create(final CreateHashedApiKeyRequest createHashedApiKeyRequest,
                        final HashedApiKeyParts hashedApiKeyParts) throws DuplicateHashException,
            DuplicatePrefixException;

    Optional<HashedApiKey> fetch(final int id);

    HashedApiKey update(final HashedApiKey apiKey);

    boolean delete(final int id);

    int delete(final Collection<Integer> ids);
}
