package stroom.security.impl.apikey;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.security.shared.ApiKey;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.ApiKeyResultPage;
import stroom.security.shared.CreateApiKeyRequest;
import stroom.security.shared.CreateApiKeyResponse;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.util.NullSafe;
import stroom.util.shared.StringUtil;

import java.util.Collection;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged()
public class ApiKeyResourceImpl implements ApiKeyResource {

    private final Provider<ApiKeyService> apiKeyServiceProvider;

    @Inject
    public ApiKeyResourceImpl(final Provider<ApiKeyService> apiKeyServiceProvider) {
        this.apiKeyServiceProvider = apiKeyServiceProvider;
    }

    @AutoLogged()
    @Override
    public CreateApiKeyResponse create(final CreateApiKeyRequest request) {
        return apiKeyServiceProvider.get().create(request);
    }

    @Override
    public ApiKey fetch(final Integer id) {
        return apiKeyServiceProvider.get().fetch(id)
                .orElse(null);
    }

    @Override
    public ApiKey update(final int id, final ApiKey apiKey) {
        return apiKeyServiceProvider.get().update(apiKey);
    }

    @Override
    public boolean delete(final int id) {
        final boolean didDelete = apiKeyServiceProvider.get().delete(id);
        if (!didDelete) {
            throw new RuntimeException("No API Key found with ID " + id);
        }
        return didDelete;
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
    public ApiKeyResultPage find(final FindApiKeyCriteria criteria) {
        return apiKeyServiceProvider.get().find(criteria);
    }
}
