package stroom.proxy.app.handler;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.StreamHandler;
import stroom.proxy.repo.StreamHandlerFactory;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Handler class that forwards the request to a URL.
 */
@Singleton
public class ForwardStreamHandlerFactory implements StreamHandlerFactory, HasHealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(ForwardStreamHandlerFactory.class);

    private final LogStream logStream;
    private final ForwardStreamConfig forwardStreamConfig;
    private final ProxyRepositoryConfig proxyRepositoryConfig;
    private final List<String> urls;
    private final List<WebTarget> dataFeedWebTargets;

    @Inject
    ForwardStreamHandlerFactory(final LogStream logStream,
                                final ForwardStreamConfig forwardStreamConfig,
                                final ProxyRepositoryConfig proxyRepositoryConfig,
                                final Client jerseyClient) {
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

        // Create WebTargets for all urls
        this.dataFeedWebTargets = urls.stream()
                .map(jerseyClient::target)
                .collect(Collectors.toList());

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

    private Optional<String> checkWebTargetHealth(final WebTarget webTarget) {
        LOGGER.debug("Sending empty health check POST to {}", webTarget);
        try {
            // send an HTTP OPTIONS request just to check the url can be reached
            try (final Response response = webTarget
                    .request()
                    .options()) {
                LOGGER.debug("Received response {}", response);
                if (response == null) {
                    return Optional.of("Unhealthy: Response is null");
                } else if (response.getStatusInfo().getStatusCode() != 200) {
                    return Optional.of(Integer.toString(response.getStatusInfo().getStatusCode()));
                } else {
                    final String allowedMethods = response.getHeaderString("Allow");
                    if (!allowedMethods.contains("POST")) {
                        return Optional.of("Unhealthy: POST method not supported");
                    }
                    return Optional.empty();
                }
            }
        } catch (Exception e) {
            return Optional.of("Unhealthy: Error sending request - " + e.getMessage());
        }
    }

    @Override
    public HealthCheck.Result getHealth() {
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();

        final AtomicBoolean allHealthy = new AtomicBoolean(true);

        final Map<URI, String> postResults = new ConcurrentHashMap<>();

        // parallelStream so we can hit multiple URLs concurrently
        dataFeedWebTargets.parallelStream()
                .forEach(webTarget -> {
                    Optional<String> errorMsg = checkWebTargetHealth(webTarget);

                    if (errorMsg.isPresent()) {
                        allHealthy.set(false);
                    }
                    postResults.put(webTarget.getUri(), errorMsg.orElse("Healthy"));
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
