package stroom.proxy.handler;

import com.codahale.metrics.health.HealthCheck;
import org.apache.commons.lang.StringUtils;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
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
    private static final List<StroomStatusCode> VALID_HEALTH_CHECK_RESPONSE_CODES = Arrays.asList(
            StroomStatusCode.OK,
            // The healthcheck will send an empty POST with no feed specified so if we get a feed not defined code back
            // it confirms the url is valid and we can reach it.
            StroomStatusCode.FEED_IS_NOT_DEFINED
    );

    private final LogStream logStream;
    private final ForwardStreamConfig forwardStreamConfig;
    private final ProxyRepositoryConfig proxyRepositoryConfig;
    private final List<String> urls;
//    private final WebTarget webtarget;

    @Inject
    ForwardStreamHandlerFactory(final LogStream logStream,
                                final ForwardStreamConfig forwardStreamConfig,
                                final ProxyRepositoryConfig proxyRepositoryConfig,
                                final Client jerseyClient) {
        this.logStream = logStream;
        this.forwardStreamConfig = forwardStreamConfig;
        this.proxyRepositoryConfig = proxyRepositoryConfig;

        if (forwardStreamConfig.isForwardingEnabled()) {
            if (StringUtils.isEmpty(forwardStreamConfig.getForwardUrl())) {
                throw new RuntimeException("Forward is enabled but no forward URLs have been configured in 'forwardUrl'");
            }
            this.urls = Arrays.asList(forwardStreamConfig.getForwardUrl().split(","));
        } else {
            this.urls = Collections.emptyList();
        }

        if (proxyRepositoryConfig.isStoringEnabled() && StringUtils.isEmpty(proxyRepositoryConfig.getRepoDir())) {
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
                && StringUtils.isNotBlank(proxyRepositoryConfig.getRepoDir());
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

                    boolean isHealthyCode = VALID_HEALTH_CHECK_RESPONSE_CODES.stream()
                            .map(StroomStatusCode::getHttpCode)
                            .map(code -> Integer.toString(code))
                            .anyMatch(code -> code.equals(msg));

                    if (!isHealthyCode) {
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
