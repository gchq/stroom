package stroom.proxy.app.forwarder;

import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.LogStream;
import stroom.proxy.repo.ProxyRepoConfig;
import stroom.receive.common.StreamHandlers;
import stroom.util.HasHealthCheck;
import stroom.util.cert.SSLUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * Handler class that forwards the request to a URL.
 */
@Singleton
public class ForwarderDestinationsImpl implements ForwarderDestinations, HasHealthCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForwarderDestinationsImpl.class);

    private static final String USER_AGENT_FORMAT = "stroom-proxy/{} java/{}";

    private final Provider<ForwarderConfig> forwarderConfigProvider;
    private final Provider<BuildInfo> buildInfoProvider;
    private final Map<String, ForwardStreamHandlers> providers;
    private final String userAgentString;

    @Inject
    public ForwarderDestinationsImpl(final LogStream logStream,
                                     final Provider<ForwarderConfig> forwarderConfigProvider,
                                     final ProxyRepoConfig proxyRepoConfig,
                                     final Provider<BuildInfo> buildInfoProvider,
                                     final PathCreator pathCreator) {
        this.forwarderConfigProvider = forwarderConfigProvider;
        this.buildInfoProvider = buildInfoProvider;
        final ForwarderConfig forwarderConfig = forwarderConfigProvider.get();

        // Set the user agent string to something like
        // stroom-proxy/v6.0-beta.46 java/1.8.0_181
        userAgentString = getUserAgentString(forwarderConfig.getUserAgent());

        if (forwarderConfig.isForwardingEnabled()) {
            if (forwarderConfig.getForwardDestinations().isEmpty()) {
                throw new RuntimeException("Forward is enabled but no forward URLs have been configured " +
                        "in 'forwardUrl'");
            }
            LOGGER.info("Initialising ForwardStreamHandlerFactory with user agent string [{}]", userAgentString);

            this.providers = forwarderConfig.getForwardDestinations()
                    .stream()
                    .map(config ->
                            new ForwardStreamHandlers(logStream, userAgentString, config, pathCreator))
                    .collect(Collectors.toMap(f -> f.getConfig().getForwardUrl(), Function.identity()));
        } else {
            LOGGER.info("Forwarding of streams is disabled");
            this.providers = Collections.emptyMap();
        }

        if (proxyRepoConfig.isStoringEnabled() && Strings.isNullOrEmpty(proxyRepoConfig.getRepoDir())) {
            throw new RuntimeException("Storing is enabled but no repo directory have been provided in 'repoDir'");
        }
    }

    @Override
    public List<String> getDestinationNames() {
        return new ArrayList<>(providers.keySet());
    }

    @Override
    public StreamHandlers getProvider(final String forwardUrl) {
        return providers.get(forwardUrl);
    }

    @Override
    public HealthCheck.Result getHealth() {
        final HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();

        final AtomicBoolean allHealthy = new AtomicBoolean(true);

        final ForwarderConfig forwarderConfig = forwarderConfigProvider.get();
        resultBuilder
                .withDetail("forwardingEnabled", forwarderConfig.isForwardingEnabled());

        if (forwarderConfig.isForwardingEnabled()) {
            final Map<String, String> postResults = new ConcurrentHashMap<>();
            // parallelStream so we can hit multiple URLs concurrently
            providers.values().forEach(destination -> {
                final String url = destination.getConfig().getForwardUrl();
                final Optional<String> errorMsg = SSLUtil.checkUrlHealth(
                        url, destination.getSslSocketFactory(), destination.getConfig().getSslConfig(), "POST");

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

    private String getUserAgentString(final String userAgentFromConfig) {
        if (userAgentFromConfig != null && !userAgentFromConfig.isEmpty()) {
            return userAgentFromConfig;
        } else {
            // Construct something like
            // stroom-proxy/v6.0-beta.46 java/1.8.0_181
            return LogUtil.message(USER_AGENT_FORMAT,
                    buildInfoProvider.get().getBuildVersion(), System.getProperty("java.version"));
        }
    }
}
