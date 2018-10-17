package stroom.proxy.handler;

import com.codahale.metrics.health.HealthCheck;
import org.apache.commons.lang.StringUtils;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
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
                    final String msg = validatePost(url);

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

    private String validatePost(final String urlStr) {
        URL url = null;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            return LambdaLogger.buildMessage("Malformed URL: [{}]", e.getMessage());
        }

        URLConnection connection = null;
        try {
            connection = url.openConnection();
        } catch (IOException e) {
            return LambdaLogger.buildMessage("Invalid URL: [{}]", e.getMessage());
        }

        if (connection instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection) connection;
            try {
                http.setRequestMethod("POST"); // PUT is another valid option
            } catch (ProtocolException e) {
                return LambdaLogger.buildMessage("Invalid protocol during test: [{}]", e.getMessage());
            }
            http.setDoOutput(true);

            try {
                http.connect();
            } catch (IOException e) {
                return LambdaLogger.buildMessage("Unable to connect: [{}]", e.getMessage());
            }

            try {
                int responseCode = http.getResponseCode();
                return String.valueOf(responseCode);
            } catch (IOException e) {
                return LambdaLogger.buildMessage("Unable to get response code: [{}]", e.getMessage());
            }
        } else {
            return LambdaLogger.buildMessage("Unknown connection type: [{}]",
                    connection.getClass().getName());
        }
    }

}
