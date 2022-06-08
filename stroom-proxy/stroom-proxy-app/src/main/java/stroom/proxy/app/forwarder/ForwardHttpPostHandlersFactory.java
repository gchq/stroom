package stroom.proxy.app.forwarder;

import stroom.proxy.repo.LogStream;
import stroom.util.cert.SSLUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.net.ssl.SSLSocketFactory;

@Singleton
public class ForwardHttpPostHandlersFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForwardHttpPostHandlersFactory.class);

    private static final String USER_AGENT_FORMAT = "stroom-proxy/{} java/{}";

    private final LogStream logStream;

    private final PathCreator pathCreator;

    private final String defaultUserAgent;


    @Inject
    public ForwardHttpPostHandlersFactory(final LogStream logStream,
                                          final PathCreator pathCreator,
                                          final Provider<BuildInfo> buildInfoProvider) {
        this.logStream = logStream;
        this.pathCreator = pathCreator;

        // Construct something like
        // stroom-proxy/v6.0-beta.46 java/1.8.0_181
        defaultUserAgent = LogUtil.message(USER_AGENT_FORMAT,
                buildInfoProvider.get().getBuildVersion(), System.getProperty("java.version"));
    }

    public ForwardHttpPostHandlers create(final ForwardHttpPostConfig config) {
        final String userAgentString;
        if (config.getUserAgent() != null && !config.getUserAgent().isEmpty()) {
            userAgentString = config.getUserAgent();
        } else {
            userAgentString = defaultUserAgent;
        }

        final SSLSocketFactory sslSocketFactory;
        LOGGER.info("Configuring SSLSocketFactory for URL {}", config.getForwardUrl());
        if (config.getSslConfig() != null) {
            sslSocketFactory = SSLUtil.createSslSocketFactory(config.getSslConfig(), pathCreator);
        } else {
            sslSocketFactory = null;
        }

        LOGGER.info("Initialising \"" +
                config.getName() +
                "\" ForwardHttpPostHandlers with user agent string [" +
                userAgentString +
                "]");
        return new ForwardHttpPostHandlers(logStream, config, userAgentString, sslSocketFactory);
    }
}
