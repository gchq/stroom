package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.security.shared.ApiKey;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.CreateApiKeyRequest;
import stroom.security.shared.CreateApiKeyResponse;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
public class ApiKeyResourceImpl implements ApiKeyResource {

    private final Provider<ApiKeyService> apiKeyServiceProvider;

    @Inject
    public ApiKeyResourceImpl(final Provider<ApiKeyService> apiKeyServiceProvider) {
        this.apiKeyServiceProvider = apiKeyServiceProvider;
    }

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
    public void delete(final int id) {
        apiKeyServiceProvider.get().delete(id);
    }

    @Override
    public ResultPage<ApiKey> find(final FindApiKeyCriteria criteria) {
        return apiKeyServiceProvider.get().find(criteria);
    }
}
