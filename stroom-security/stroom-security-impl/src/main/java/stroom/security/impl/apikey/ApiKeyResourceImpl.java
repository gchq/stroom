package stroom.security.impl.apikey;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.CreateHashedApiKeyRequest;
import stroom.security.shared.CreateHashedApiKeyResponse;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringUtil;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Collection;
import java.util.Objects;

@AutoLogged()
public class ApiKeyResourceImpl implements ApiKeyResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ApiKeyResourceImpl.class);

    private final Provider<ApiKeyService> apiKeyServiceProvider;

    @Inject
    public ApiKeyResourceImpl(final Provider<ApiKeyService> apiKeyServiceProvider) {
        this.apiKeyServiceProvider = apiKeyServiceProvider;
    }

    @AutoLogged()
    @Override
    public CreateHashedApiKeyResponse create(final CreateHashedApiKeyRequest request) {
        return apiKeyServiceProvider.get().create(request);
    }

    @Override
    public HashedApiKey fetch(final Integer id) {
        return apiKeyServiceProvider.get().fetch(id)
                .orElse(null);
    }

    @Override
    public HashedApiKey update(final int id, final HashedApiKey apiKey) {
        return apiKeyServiceProvider.get().update(apiKey);
    }

    @Override
    public boolean delete(final int id) {
        try {
            final boolean didDelete = apiKeyServiceProvider.get().delete(id);
            if (!didDelete) {
                throw new RuntimeException("No API Key found with ID " + id);
            }
            return true;
        } catch (final Exception e) {
            LOGGER.debug(() -> LogUtil.message("Error deleting API key with ID {}: {}",
                    id, LogUtil.exceptionMessage(e)));
            throw e;
        }
    }

    @Override
    public int deleteBatch(final Collection<Integer> ids) {
        if (NullSafe.hasItems(ids)) {
            final int count = apiKeyServiceProvider.get().deleteBatch(ids);
            if (ids.size() != count) {
                throw new RuntimeException("Only found " + count
                                           + " out of " + ids.size()
                                           + " API Key" + StringUtil.pluralSuffix(ids.size())
                                           + " to delete.");
            }
            return count;
        } else {
            return 0;
        }
    }

    @Override
    public ResultPage<HashedApiKey> find(final FindApiKeyCriteria criteria) {
        return apiKeyServiceProvider.get().find(criteria);
    }

    @Override
    public UserDesc verifyApiKey(final VerifyApiKeyRequest request) {
        LOGGER.debug("verifyApiKey() - request: {}", request);
        Objects.requireNonNull(request);
        // Null return is mapped to 204 status
        final UserDesc userDesc = apiKeyServiceProvider.get().verifyApiKey(request)
                .orElse(null);
        LOGGER.debug("verifyApiKey() - Returning userDesc: {}, request: {}", userDesc, request);
        return userDesc;
    }
}
