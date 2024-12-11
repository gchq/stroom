package stroom.security.impl.apikey;

import stroom.security.impl.HashedApiKeyParts;
import stroom.security.impl.apikey.ApiKeyService.DuplicateApiKeyException;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.util.NullSafe;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.shared.UserRef;

import java.util.Collection;
import java.util.List;
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
