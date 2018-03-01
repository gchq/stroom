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
    }

    @Override
    public List<StreamHandler> addReceiveHandlers(final List<StreamHandler> handlers) {
        if (proxyRepositoryConfig != null && StringUtils.isNotBlank(proxyRepositoryConfig.getRepoDir())) {
            handlers.add(proxyRepositoryStreamHandlerProvider.get());
        }
        return handlers;
    }

    @Override
    public List<StreamHandler> addSendHandlers(final List<StreamHandler> handlers) {
        // Do nothing.
        return handlers;
    }
}
