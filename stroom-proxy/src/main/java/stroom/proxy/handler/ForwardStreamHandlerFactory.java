package stroom.proxy.handler;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Strings;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handler class that forwards the request to a URL.
 */
@Singleton
public class ForwardStreamHandlerFactory implements StreamHandlerFactory, HasHealthCheck {
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

        if (forwardStreamConfig.isForwardingEnabled()) {
            if (Strings.isNullOrEmpty(forwardStreamConfig.getForwardUrl())) {
                throw new RuntimeException("Forward is enabled but no forward URLs have been configured in 'forwardUrl'");
            }
            this.urls = Arrays.asList(forwardStreamConfig.getForwardUrl().split(","));
        } else {
            this.urls = Collections.emptyList();
        }

        if (proxyRepositoryConfig.isStoringEnabled() && Strings.isNullOrEmpty(proxyRepositoryConfig.getDir())) {
            throw new RuntimeException("Storing is enabled but no repo directory have been provided in 'repoDir'");
        }
    }

    @Override
    public List<StreamHandler> addReceiveHandlers(final List<StreamHandler> handlers) {
        if (!proxyRepositoryConfig.isStoringEnabled()) {
            add(handlers);
        }
        return handlers;
    }

    @Override
    public List<StreamHandler> addSendHandlers(final List<StreamHandler> handlers) {
        if (isConfiguredToStore()) {
            add(handlers);
        }
        return handlers;
    }

    private boolean isConfiguredToStore() {
        return proxyRepositoryConfig.isStoringEnabled()
                && !Strings.isNullOrEmpty(proxyRepositoryConfig.getDir());
    }

    private void add(final List<StreamHandler> handlers) {
        urls.forEach(url ->
                handlers.add(new ForwardStreamHandler(
                        logStream,
                        url,
                        forwardStreamConfig.getForwardTimeoutMs(),
                        forwardStreamConfig.getForwardDelayMs(),
                        forwardStreamConfig.getForwardChunkSize())));
    }

    @Override
    public HealthCheck.Result getHealth() {
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();

        final AtomicBoolean allHealthy = new AtomicBoolean(true);

        final Map<String, String> postResults = new ConcurrentHashMap<>();

        // parallelStream so we can hit multiple URLs concurrently
        urls.parallelStream()
                .forEach(url -> {
                    final String msg = HealthCheckUtils.validateHttpConnection("POST", url);

                    if (!"200".equals(msg)) {
                        allHealthy.set(false);
                    }
                    postResults.put(url, msg);
                });

        resultBuilder.withDetail("forwardUrls", postResults);
        if (allHealthy.get()) {
            resultBuilder.healthy();
        } else {
            resultBuilder.unhealthy();
        }
        return resultBuilder.build();
    }


}
