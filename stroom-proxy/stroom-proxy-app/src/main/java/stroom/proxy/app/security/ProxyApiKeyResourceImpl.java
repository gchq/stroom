package stroom.proxy.app.security;

import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.core.NoContentException;

import java.util.Objects;

public class ProxyApiKeyResourceImpl implements ProxyApiKeyResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyApiKeyResourceImpl.class);

    private final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider;

    @Inject
    public ProxyApiKeyResourceImpl(final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider) {
        this.proxyApiKeyServiceProvider = proxyApiKeyServiceProvider;
    }

    @Override
    public UserDesc verifyApiKey(final VerifyApiKeyRequest request) throws NoContentException {
        LOGGER.debug("verifyApiKey() - request: {}", request);
        Objects.requireNonNull(request);
        // Null return is mapped to 204 status
        final UserDesc userDesc = proxyApiKeyServiceProvider.get().verifyApiKey(request)
                .orElse(null);
        LOGGER.debug("verifyApiKey() - Returning userDesc: {}, request: {}", userDesc, request);
        return userDesc;
    }
}
