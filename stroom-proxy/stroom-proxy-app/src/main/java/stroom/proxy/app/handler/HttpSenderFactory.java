package stroom.proxy.app.handler;

import stroom.proxy.repo.LogStream;
import stroom.security.api.UserIdentityFactory;
import stroom.util.http.HttpClientFactory;
import stroom.util.logging.LogUtil;
import stroom.util.metrics.Metrics;
import stroom.util.shared.BuildInfo;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.hc.client5.http.classic.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Singleton
public class HttpSenderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpSenderFactory.class);

    private static final String USER_AGENT_FORMAT = "stroom-proxy/{} java/{}";

    private final LogStream logStream;
    private final String defaultUserAgent;
    private final UserIdentityFactory userIdentityFactory;
    private final HttpClientFactory httpClientFactory;
    private final Metrics metrics;

    @Inject
    public HttpSenderFactory(final LogStream logStream,
                             final Provider<BuildInfo> buildInfoProvider,
                             final UserIdentityFactory userIdentityFactory,
                             final HttpClientFactory httpClientFactory,
                             final Metrics metrics) {
        this.logStream = logStream;
        this.userIdentityFactory = userIdentityFactory;
        this.httpClientFactory = httpClientFactory;
        this.metrics = metrics;

        // Construct something like
        // stroom-proxy/v6.0-beta.46 java/1.8.0_181
        defaultUserAgent = LogUtil.message(USER_AGENT_FORMAT,
                buildInfoProvider.get().getBuildVersion(), System.getProperty("java.version"));
    }

    public HttpSender create(final ForwardHttpPostConfig config) {
        final String userAgentString;
        if (config.getHttpClient() != null &&
            config.getHttpClient().getUserAgent() != null) {
            userAgentString = config.getHttpClient().getUserAgent();
        } else {
            userAgentString = defaultUserAgent;
        }

        LOGGER.info("Initialising \"" +
                    config.getName() +
                    "\" ForwardHttpPostHandlers with user agent string [" +
                    userAgentString +
                    "]");

        String name = "HttpSender";
        if (config.getName() != null) {
            name += "-" + config.getName();
        }
        name += "-" + UUID.randomUUID();

        final HttpClient httpClient = httpClientFactory.get(name, config.getHttpClient());
        return new HttpSender(
                logStream,
                config,
                userAgentString,
                userIdentityFactory,
                httpClient,
                metrics);
    }
}
