package stroom.security.impl.apikey;

import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.apikey.ApiKeyService.DuplicateHashException;
import stroom.security.shared.ApiKey;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.CreateApiKeyRequest;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.util.NullSafe;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.shared.UserName;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApiKeyDao {

    FilterFieldMappers<ApiKey> FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_OWNER_DISPLAY_NAME, (ApiKey apiKey) ->
                    NullSafe.get(apiKey.getOwner(), UserName::getUserIdentityForAudit)),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_NAME, ApiKey::getName),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_PREFIX, ApiKey::getApiKeyPrefix),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_COMMENTS, ApiKey::getComments),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_ENABLED,
                    apiKey -> apiKey.getEnabled()
                            ? "enabled"
                            : "disabled"));

    ApiKeyResultPage find(final FindApiKeyCriteria criteria);

    /**
     * Verify an API key, ensuring it exists and is enabled, returning the stroom user
     * UUID of the verified user.
     * @param apiKeyHash The API key hash to verify.
     * @return The stroom user UUID of the verified user.
     * If the API key doesn't exist or is disabled/expired, an empty {@link Optional}
     * will be returned.
     */
    Optional<String> fetchVerifiedUserUuid(final String apiKeyHash);

    /**
     * Fetch all API keys matching the given API key prefix.
     */
    List<ApiKey> fetchValidApiKeysByPrefix(final String apiKeyPrefix);

    ApiKey create(final CreateApiKeyRequest createApiKeyRequest,
                  final HashedApiKeyParts hashedApiKeyParts) throws DuplicateHashException;

    Optional<ApiKey> fetch(final int id);

    ApiKey update(final ApiKey apiKey);

    boolean delete(final int id);

    int delete(final Collection<Integer> ids);
}
