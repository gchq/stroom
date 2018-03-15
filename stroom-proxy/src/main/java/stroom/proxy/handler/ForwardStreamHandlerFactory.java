package stroom.proxy.handler;

import com.google.common.base.Strings;
import stroom.proxy.repo.ProxyRepositoryConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Handler class that forwards the request to a URL.
 */
@Singleton
public class ForwardStreamHandlerFactory implements StreamHandlerFactory {
    private final LogStream logStream;
    private final ForwardStreamConfig forwardStreamConfig;
    private final ProxyRepositoryConfig proxyRepositoryConfig;
    private final List<String> urls;

    @Inject
    ForwardStreamHandlerFactory(final LogStream logStream,
                                final ForwardStreamConfig forwardStreamConfig,
                                final ProxyRepositoryConfig proxyRepositoryConfig) {
        this.logStream = logStream;
        this.forwardStreamConfig = forwardStreamConfig;
        this.proxyRepositoryConfig = proxyRepositoryConfig;

        if (forwardStreamConfig != null && forwardStreamConfig.getForwardUrl() != null && forwardStreamConfig.getForwardUrl().length() > 0) {
            this.urls = Arrays.asList(forwardStreamConfig.getForwardUrl().split(","));
        } else {
            this.urls = Collections.emptyList();
        }
    }

    @Override
    public List<StreamHandler> addReceiveHandlers(final List<StreamHandler> handlers) {
        if (proxyRepositoryConfig == null || Strings.isNullOrEmpty(proxyRepositoryConfig.getRepoDir())) {
            add(handlers);
        }
        return handlers;
    }

    @Override
    public List<StreamHandler> addSendHandlers(final List<StreamHandler> handlers) {
        if (proxyRepositoryConfig != null && !Strings.isNullOrEmpty(proxyRepositoryConfig.getRepoDir())) {
            add(handlers);
        }
        return handlers;
    }

    private void add(final List<StreamHandler> handlers) {
        urls.forEach(url -> handlers.add(new ForwardStreamHandler(logStream, url, forwardStreamConfig.getForwardTimeoutMs(), forwardStreamConfig.getForwardDelayMs(), forwardStreamConfig.getForwardChunkSize())));
    }
}
