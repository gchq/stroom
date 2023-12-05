package stroom.security.impl;

import stroom.security.shared.ApiKey;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.util.NullSafe;
import stroom.util.filter.FilterFieldMapper;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import java.util.Optional;

public interface ApiKeyDao extends HasIntCrud<ApiKey> {

    FilterFieldMappers<ApiKey> FILTER_FIELD_MAPPERS = FilterFieldMappers.of(
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_OWNER_DISPLAY_NAME, (ApiKey apiKey) ->
                    NullSafe.get(apiKey.getOwner(), UserName::getDisplayName)),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_NAME, ApiKey::getName),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_COMMENTS, ApiKey::getComments),
            FilterFieldMapper.of(FindApiKeyCriteria.FIELD_DEF_ENABLED,
                    apiKey -> apiKey.getEnabled()
                            ? "Enabled"
                            : "Disabled"));

    ResultPage<ApiKey> find(final FindApiKeyCriteria criteria);

    /**
     * Verify an API key, ensuring it exists and is enabled, returning the stroom user
     * UUID of the verified user.
     * @param apiKey The API key to verify.
     * @return The stroom user UUID of the verified user.
     * If the API key doesn't exist or is disabled, an empty {@link Optional}
     * will be returned.
     */
    Optional<String> fetchVerifiedIdentity(final String apiKey);

}
