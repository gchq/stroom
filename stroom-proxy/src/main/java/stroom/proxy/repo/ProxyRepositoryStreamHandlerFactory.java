package stroom.proxy.repo;

import org.apache.commons.lang.StringUtils;
import stroom.proxy.handler.StreamHandler;
import stroom.proxy.handler.StreamHandlerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class ProxyRepositoryStreamHandlerFactory implements StreamHandlerFactory {
    private final ProxyRepositoryConfig proxyRepositoryConfig;
    private final Provider<ProxyRepositoryStreamHandler> proxyRepositoryStreamHandlerProvider;

    @Inject
    public ProxyRepositoryStreamHandlerFactory(final ProxyRepositoryConfig proxyRepositoryConfig,
                                               final Provider<ProxyRepositoryStreamHandler> proxyRepositoryStreamHandlerProvider) {
        this.proxyRepositoryConfig = proxyRepositoryConfig;
        this.proxyRepositoryStreamHandlerProvider = proxyRepositoryStreamHandlerProvider;

        if (proxyRepositoryConfig.isStoringEnabled() && StringUtils.isEmpty(proxyRepositoryConfig.getRepoDir())) {
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
        return proxyRepositoryConfig.isStoringEnabled()
                && StringUtils.isNotBlank(proxyRepositoryConfig.getRepoDir());
    }
}
