package stroom.security.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.security.api.SecurityContext;
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

    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<ApiKeyService> apiKeyServiceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    public ApiKeyResourceImpl(final Provider<SecurityContext> securityContextProvider,
                              final Provider<ApiKeyService> apiKeyServiceProvider,
                              final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.securityContextProvider = securityContextProvider;
        this.apiKeyServiceProvider = apiKeyServiceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Override
    public CreateApiKeyResponse create(final CreateApiKeyRequest request) {
        return securityContextProvider.get().secureResult(() ->
                apiKeyServiceProvider.get().create(request));
    }

    @Override
    public ApiKey fetch(final Integer id) {
        return securityContextProvider.get().secureResult(() ->
                apiKeyServiceProvider.get().fetch(id)
                        .orElse(null));
    }

    @Override
    public ApiKey update(final int id, final ApiKey apiKey) {
        return securityContextProvider.get().secureResult(() ->
                apiKeyServiceProvider.get().update(apiKey));
    }

    @Override
    public void delete(final int id) {
        securityContextProvider.get().secure(() ->
                apiKeyServiceProvider.get().delete(id));
    }

    @Override
    public ResultPage<ApiKey> find(final FindApiKeyCriteria criteria) {
        return securityContextProvider.get().secureResult(() ->
                apiKeyServiceProvider.get().find(criteria));
    }

}
