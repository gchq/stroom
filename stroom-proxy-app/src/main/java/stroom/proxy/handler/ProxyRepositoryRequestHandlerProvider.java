package stroom.proxy.handler;

import com.google.inject.Inject;
import com.google.inject.Provider;
import stroom.proxy.repo.ProxyRepositoryManager;

public class ProxyRepositoryRequestHandlerProvider implements Provider<ProxyRepositoryRequestHandler> {
    private final ProxyRepositoryManager proxyRepositoryManager;

    @Inject
    public ProxyRepositoryRequestHandlerProvider(final ProxyRepositoryManager proxyRepositoryManager) {
        this.proxyRepositoryManager = proxyRepositoryManager;
    }

    @Override
    public ProxyRepositoryRequestHandler get() {
        return new ProxyRepositoryRequestHandler(proxyRepositoryManager);
    }
}
