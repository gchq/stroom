package stroom.app;

import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.common.UriConfig;
import stroom.config.common.UriFactory;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;

public class UriFactoryImpl implements UriFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(UriFactoryImpl.class);

    private final AppConfig appConfig;
    private final Config config;

    private String localBaseUri;
    private String publicBaseUri;

    @Inject
    public UriFactoryImpl(final Config config,
                          final AppConfig appConfig) {
        this.config = config;
        this.appConfig = appConfig;

        final String localBaseUri = getLocalBaseUri();
        LOGGER.info("Established Local URI: " + localBaseUri);
        final String publicBaseUri = getPublicBaseUri();
        LOGGER.info("Established Public URI: " + publicBaseUri);
    }

    @Override
    public String localUriString(final String path) {
        return buildAbsoluteUrl(getLocalBaseUri(), path);
    }

    @Override
    public URI localURI(final String path) {
        try {
            return new URI(localUriString(path));
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public String publicUriString(final String path) {
        return buildAbsoluteUrl(getPublicBaseUri(), path);
    }

    @Override
    public URI publicURI(final String path) {
        try {
            return new URI(publicUriString(path));
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String getPublicBaseUri() {
        if (publicBaseUri == null) {
            if (isValid(appConfig.getPublicUri())) {
                publicBaseUri = appConfig.getPublicUri().toString();
            } else {
                publicBaseUri = getLocalBaseUri();
            }
        }
        return publicBaseUri;
    }

    private String getLocalBaseUri() {
        if (localBaseUri == null) {
            if (isValid(appConfig.getNodeUri())) {
                localBaseUri = appConfig.getNodeUri().toString();
            } else {
                if (config.getServerFactory() instanceof DefaultServerFactory) {
                    final DefaultServerFactory defaultServerFactory = (DefaultServerFactory) config.getServerFactory();
                    if (defaultServerFactory.getApplicationConnectors().size() > 0) {
                        final ConnectorFactory connectorFactory = defaultServerFactory.getApplicationConnectors().get(0);

                        if (connectorFactory instanceof HttpsConnectorFactory) {
                            final HttpsConnectorFactory httpsConnectorFactory = (HttpsConnectorFactory) connectorFactory;
                            final UriConfig uriConfig = new UriConfig();
                            uriConfig.setScheme("https");
                            uriConfig.setHostname(resolveHost(httpsConnectorFactory.getBindHost()));
                            uriConfig.setPort(httpsConnectorFactory.getPort());
                            uriConfig.setPathPrefix(defaultServerFactory.getApplicationContextPath());
                            localBaseUri = uriConfig.toString();

                        } else if (connectorFactory instanceof HttpConnectorFactory) {
                            final HttpConnectorFactory httpConnectorFactory = (HttpConnectorFactory) connectorFactory;
                            final UriConfig uriConfig = new UriConfig();
                            uriConfig.setScheme("http");
                            uriConfig.setHostname(resolveHost(httpConnectorFactory.getBindHost()));
                            uriConfig.setPort(httpConnectorFactory.getPort());
                            uriConfig.setPathPrefix(defaultServerFactory.getApplicationContextPath());
                            localBaseUri = uriConfig.toString();
                        }
                    }
                }

                if (localBaseUri == null) {
                    throw new NullPointerException("Unable to set local base URI");
                }
            }
        }
        return localBaseUri;
    }

    private String resolveHost(final String bindHost) {
        if (bindHost != null && bindHost.length() > 0) {
            return bindHost;
        }

        return "localhost";

//        try {
//            return InetAddress.getLocalHost().getHostName();
//        } catch (final UnknownHostException e) {
//            LOGGER.error(e.getMessage(), e);
//            throw new RuntimeException(e.getMessage(), e);
//        }
    }

    private boolean isValid(final UriConfig uriConfig) {
        if (uriConfig == null) {
            return false;
        }
        if (uriConfig.getScheme() == null || uriConfig.getScheme().isEmpty()) {
            return false;
        }
        if (uriConfig.getHostname() == null || uriConfig.getHostname().isEmpty()) {
            return false;
        }
        return true;
    }

    /**
     * Helper method to build a URL on the API Gateway using the supplied
     * path
     *
     * @param path e.g. /users
     */
    private String buildAbsoluteUrl(final String basePath, final String path) {
        final StringBuilder sb = new StringBuilder();

        if (basePath != null) {
            sb.append(basePath);
            if (basePath.endsWith("/")) {
                sb.setLength(sb.length() - 1);
            }
        }

        if (path != null && !path.isEmpty()) {
            if (!path.startsWith("/")) {
                sb.append("/");
            }
            sb.append(path);
        }
        return sb.toString();
    }
}
