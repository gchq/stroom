package stroom.proxy.repo;

import com.google.common.base.Strings;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class ProxyRepositoryStreamHandlerFactory implements StreamHandlerFactory {

    private final Provider<ProxyRepositoryConfig> proxyRepositoryConfigProvider;
    private final Provider<ProxyRepositoryStreamHandler> proxyRepositoryStreamHandlerProvider;

    @Inject
    public ProxyRepositoryStreamHandlerFactory(
            final Provider<ProxyRepositoryConfig> proxyRepositoryConfigProvider,
            final Provider<ProxyRepositoryStreamHandler> proxyRepositoryStreamHandlerProvider) {

        this.proxyRepositoryConfigProvider = proxyRepositoryConfigProvider;
        this.proxyRepositoryStreamHandlerProvider = proxyRepositoryStreamHandlerProvider;

        final ProxyRepositoryConfig proxyRepositoryConfig = proxyRepositoryConfigProvider.get();
        if (proxyRepositoryConfig.isStoringEnabled() && Strings.isNullOrEmpty(proxyRepositoryConfig.getRepoDir())) {
            throw new RuntimeException("Storing is enabled but no repo directory have been provided in 'repoDir'");
        }
    }

    @Override
    public List<StreamHandler> addReceiveHandlers(final List<StreamHandler> handlers) {
        if (isConfiguredToStore()) {
            handlers.add(proxyRepositoryStreamHandlerProvider.get());
        }
        return handlers;
    }

    @Override
    public List<StreamHandler> addSendHandlers(final List<StreamHandler> handlers) {
        // Do nothing.
        return handlers;
    }

    private boolean isConfiguredToStore() {
        final ProxyRepositoryConfig proxyRepositoryConfig = proxyRepositoryConfigProvider.get();
        return proxyRepositoryConfig.isStoringEnabled()
                && !Strings.isNullOrEmpty(proxyRepositoryConfig.getRepoDir());
    }
}
