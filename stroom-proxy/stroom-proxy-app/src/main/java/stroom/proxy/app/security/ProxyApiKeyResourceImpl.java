package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Optional;

public class ProxyApiKeyResourceImpl implements ProxyApiKeyResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyApiKeyResourceImpl.class);

    private final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider;

    @Inject
    public ProxyApiKeyResourceImpl(final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider) {
        this.proxyApiKeyServiceProvider = proxyApiKeyServiceProvider;
    }

    @Override
    public UserDesc verifyApiKey(final VerifyApiKeyRequest request) {
        LOGGER.debug("verifyApiKey() - request: {}", request);
        final UserDesc userDesc = proxyApiKeyServiceProvider.get().verifyApiKey(request)
                .orElse(null);
        LOGGER.debug("verifyApiKey() - returning: {}", userDesc);
        return userDesc;
    }

    @Override
    public Optional<UserIdentity> verifyIdentity(final String apiKey) {
        return proxyApiKeyServiceProvider.get().verifyIdentity(apiKey);
    }
}
