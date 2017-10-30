package stroom.proxy.datafeed;

import org.apache.commons.lang.StringUtils;
import stroom.proxy.handler.ForwardRequestHandlerFactory;
import stroom.proxy.handler.LogRequestConfig;
import stroom.proxy.handler.LogRequestHandler;
import stroom.proxy.handler.RequestHandler;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryRequestHandler;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton bean that re-reads the proxy.properties and re-creates the app
 * context if the config has changed.
 */
public class ProxyHandlerFactory {
    private final LogRequestConfig logRequestConfig;
    private final ProxyRepositoryConfig proxyRepositoryConfig;

    private final Provider<ProxyRepositoryRequestHandler> proxyRepositoryRequestHandlerProxider;
    private final Provider<LogRequestHandler> logRequestHandlerProvider;
    private final ForwardRequestHandlerFactory forwardRequestHandlerFactory;

    @Inject
    ProxyHandlerFactory(final LogRequestConfig logRequestConfig,
                        final ProxyRepositoryConfig proxyRepositoryConfig,
                        final Provider<ProxyRepositoryRequestHandler> proxyRepositoryRequestHandlerProxider,
                        final Provider<LogRequestHandler> logRequestHandlerProvider,
                        final ForwardRequestHandlerFactory forwardRequestHandlerFactory) {
        this.logRequestConfig = logRequestConfig;
        this.proxyRepositoryConfig = proxyRepositoryConfig;
        this.proxyRepositoryRequestHandlerProxider = proxyRepositoryRequestHandlerProxider;
        this.logRequestHandlerProvider = logRequestHandlerProvider;
        this.forwardRequestHandlerFactory = forwardRequestHandlerFactory;
    }

    /**
     * Load the properties and create out configuration.
     */
    public List<RequestHandler> createIncomingHandlers() {
        final List<RequestHandler> handlerList = new ArrayList<>();
        if (StringUtils.isNotBlank(logRequestConfig.getLogRequest())) {
            handlerList.add(logRequestHandlerProvider.get());
        }
        if (StringUtils.isNotBlank(proxyRepositoryConfig.getRepoDir())) {
            handlerList.add(proxyRepositoryRequestHandlerProxider.get());
        } else {
            handlerList.addAll(forwardRequestHandlerFactory.create());
        }

        return handlerList;
    }

    /**
     * Load the properties and create out configuration.
     */
    public List<RequestHandler> createOutgoingHandlers() {
        final List<RequestHandler> handlerList = new ArrayList<>();

        if (StringUtils.isNotBlank(logRequestConfig.getLogRequest())) {
            handlerList.add(logRequestHandlerProvider.get());
        }
        if (StringUtils.isNotBlank(proxyRepositoryConfig.getRepoDir())) {
            handlerList.addAll(forwardRequestHandlerFactory.create());
        }

        return handlerList;
    }
}
