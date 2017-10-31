package stroom.proxy.handler;

import org.apache.commons.lang.StringUtils;
import stroom.proxy.handler.ForwardStreamHandlerFactory;
import stroom.proxy.handler.LogStreamConfig;
import stroom.proxy.handler.LogStreamHandler;
import stroom.proxy.handler.StreamHandler;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryStreamHandler;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton bean that re-reads the proxy.properties and re-creates the app
 * context if the config has changed.
 */
public class StreamHandlerFactoryImpl {
    private final LogStreamConfig logStreamConfig;
    private final ProxyRepositoryConfig proxyRepositoryConfig;

    private final Provider<ProxyRepositoryStreamHandler> proxyRepositoryStreamHandlerProxider;
    private final Provider<LogStreamHandler> logStreamHandlerProvider;
    private final ForwardStreamHandlerFactory forwardStreamHandlerFactory;

    @Inject
    StreamHandlerFactoryImpl(@Nullable final LogStreamConfig logStreamConfig,
                             @Nullable final ProxyRepositoryConfig proxyRepositoryConfig,
                             final Provider<ProxyRepositoryStreamHandler> proxyRepositoryStreamHandlerProxider,
                             final Provider<LogStreamHandler> logStreamHandlerProvider,
                             final ForwardStreamHandlerFactory forwardStreamHandlerFactory) {
        this.logStreamConfig = logStreamConfig;
        this.proxyRepositoryConfig = proxyRepositoryConfig;
        this.proxyRepositoryStreamHandlerProxider = proxyRepositoryStreamHandlerProxider;
        this.logStreamHandlerProvider = logStreamHandlerProvider;
        this.forwardStreamHandlerFactory = forwardStreamHandlerFactory;
    }

    /**
     * Load the properties and create out configuration.
     */
    public List<StreamHandler> createIncomingHandlers() {
        final List<StreamHandler> handlerList = new ArrayList<>();
        if (logStreamConfig != null && StringUtils.isNotBlank(logStreamConfig.getLogRequest())) {
            handlerList.add(logStreamHandlerProvider.get());
        }
        if (proxyRepositoryConfig != null && StringUtils.isNotBlank(proxyRepositoryConfig.getRepoDir())) {
            handlerList.add(proxyRepositoryStreamHandlerProxider.get());
        } else {
            handlerList.addAll(forwardStreamHandlerFactory.create());
        }

        return handlerList;
    }

    /**
     * Load the properties and create out configuration.
     */
    public List<StreamHandler> createOutgoingHandlers() {
        final List<StreamHandler> handlerList = new ArrayList<>();

        if (logStreamConfig != null && StringUtils.isNotBlank(logStreamConfig.getLogRequest())) {
            handlerList.add(logStreamHandlerProvider.get());
        }
        if (proxyRepositoryConfig != null && StringUtils.isNotBlank(proxyRepositoryConfig.getRepoDir())) {
            handlerList.addAll(forwardStreamHandlerFactory.create());
        }

        return handlerList;
    }
}
