package stroom.proxy.app.security;

import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

public class ProxyApiKeyResourceImpl implements ProxyApiKeyResource {

    private final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider;

    @Inject
    public ProxyApiKeyResourceImpl(final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider) {
        this.proxyApiKeyServiceProvider = proxyApiKeyServiceProvider;
    }

    @Override
    public UserDesc verifyApiKey(final VerifyApiKeyRequest request) {
        return proxyApiKeyServiceProvider.get().verifyApiKey(request)
                .orElse(null);
    }
}
