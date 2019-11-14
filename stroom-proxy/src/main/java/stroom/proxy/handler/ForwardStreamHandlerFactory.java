package stroom.proxy.handler;

import com.codahale.metrics.health.HealthCheck;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.util.BuildInfoUtil;
import stroom.util.HasHealthCheck;
import stroom.util.cert.SSLUtil;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.SSLSocketFactory;
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

    private static final String USER_AGENT_FORMAT = "stroom-proxy/{} java/{}";

    private final LogStream logStream;
    private final ForwardStreamConfig forwardStreamConfig;
    private final ProxyRepositoryConfig proxyRepositoryConfig;
    private final List<ForwardDestination> destinations;
    private final String userAgentString;

    @Inject
    ForwardStreamHandlerFactory(final LogStream logStream,
                                final ForwardStreamConfig forwardStreamConfig,
                                final ProxyRepositoryConfig proxyRepositoryConfig) {
        this.logStream = logStream;
        this.forwardStreamConfig = forwardStreamConfig;
        this.proxyRepositoryConfig = proxyRepositoryConfig;

        // Set the user agent string to something like
        // stroom-proxy/v6.0-beta.46 java/1.8.0_181
        userAgentString = getUserAgentString(forwardStreamConfig.getUserAgent());

        if (forwardStreamConfig.isForwardingEnabled()) {
            if (forwardStreamConfig.getForwardDestinations().isEmpty()) {
                throw new RuntimeException("Forward is enabled but no forward URLs have been configured in 'forwardUrl'");
            }
            LOGGER.info("Initialising ForwardStreamHandlerFactory with user agent string [{}]", userAgentString);

            this.destinations = forwardStreamConfig.getForwardDestinations()
                    .stream()
                    .map(config -> {
                        LOGGER.info("Configuring SSLSocketFactory for destination {}", config.getForwardUrl());
                        SSLSocketFactory sslSocketFactory = null;
                        if (config.getSslConfig() != null) {
                            sslSocketFactory = SSLUtil.createSslSocketFactory(config.getSslConfig());
                        }
                        return new ForwardDestination(config, sslSocketFactory);
                    })
                    .collect(Collectors.toList());
        } else {
            LOGGER.info("Forwarding of streams is disabled");
            this.destinations = Collections.emptyList();
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
        destinations.forEach(destination -> handlers.add(new ForwardStreamHandler(
                logStream,
                destination.config,
                destination.sslSocketFactory,
                userAgentString)));
    }

    @Override
    public HealthCheck.Result getHealth() {
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();

        final AtomicBoolean allHealthy = new AtomicBoolean(true);

        resultBuilder
                .withDetail("forwardingEnabled", forwardStreamConfig.isForwardingEnabled());

        if (forwardStreamConfig.isForwardingEnabled()) {
            final Map<String, String> postResults = new ConcurrentHashMap<>();
            // parallelStream so we can hit multiple URLs concurrently
            destinations.forEach(destination -> {
                final String url = destination.config.getForwardUrl();
                final Optional<String> errorMsg = SSLUtil.checkUrlHealth(
                        url, destination.sslSocketFactory, destination.config.getSslConfig(), "POST");

                if (errorMsg.isPresent()) {
                    allHealthy.set(false);
                }
                postResults.put(url, errorMsg.orElse("Healthy"));
            });
            resultBuilder
                    .withDetail("forwardUrls", postResults);
        }

        if (allHealthy.get()) {
            resultBuilder.healthy();
        } else {
            resultBuilder.unhealthy();
        }
        return resultBuilder.build();
    }

    public static String getUserAgentString(final String userAgentFromConfig) {
        if (userAgentFromConfig != null && !userAgentFromConfig.isEmpty()) {
            return userAgentFromConfig;
        } else {
            // Construct something like
            // stroom-proxy/v6.0-beta.46 java/1.8.0_181
            return LambdaLogger.buildMessage(USER_AGENT_FORMAT,
                    BuildInfoUtil.getBuildVersion(), System.getProperty("java.version"));
        }
    }

    private static class ForwardDestination {
        private final ForwardDestinationConfig config;
        private final SSLSocketFactory sslSocketFactory;

        private ForwardDestination(final ForwardDestinationConfig config, final SSLSocketFactory sslSocketFactory) {
            this.config = config;
            this.sslSocketFactory = sslSocketFactory;
        }
    }
}
