package stroom.app.uri;

import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.common.NodeUriConfig;
import stroom.config.common.UriConfig;
import stroom.config.common.UriFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;

@Singleton
class UriFactoryImpl implements UriFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(UriFactoryImpl.class);

    private final AppConfig appConfig;
    private final Config config;

    private String localBaseUri;
    private String publicBaseUri;
    private String uiBaseUri;

    @Inject
    UriFactoryImpl(final Config config,
                   final AppConfig appConfig) {
        this.config = config;
        this.appConfig = appConfig;

        final String localBaseUri = getLocalBaseUri();
        LOGGER.info("Established Local URI:  " + localBaseUri);
        final String publicBaseUri = getPublicBaseUri();
        LOGGER.info("Established Public URI: " + publicBaseUri);
        final String uiBaseUri = getUiBaseUri();
        LOGGER.info("Established UI URI:     " + uiBaseUri);
    }

    @Override
    public URI nodeUri(final String path) {
        return toUri(buildAbsoluteUrl(getLocalBaseUri(), path));
    }

    @Override
    public URI publicUri(final String path) {
        return toUri(buildAbsoluteUrl(getPublicBaseUri(), path));
    }

    @Override
    public URI uiUri(final String path) {
        return toUri(buildAbsoluteUrl(getUiBaseUri(), path));
    }

    private URI toUri(final String path) {
        try {
            return new URI(path);
        } catch (final URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private String getUiBaseUri() {
        if (uiBaseUri == null) {
            if (isValid(appConfig.getUiUri())) {
                uiBaseUri = appConfig.getUiUri().toString();
            } else {
                uiBaseUri = getPublicBaseUri();
            }
        }
        return uiBaseUri;
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
                localBaseUri = discoverLocalBaseUri();

                if (localBaseUri == null) {
                    throw new NullPointerException("Unable to set local base URI");
                }
            }
        }
        return localBaseUri;
    }

    private String discoverLocalBaseUri() {
        String localBaseUri = null;
        if (config.getServerFactory() instanceof DefaultServerFactory) {
            final DefaultServerFactory defaultServerFactory = (DefaultServerFactory) config.getServerFactory();
            if (defaultServerFactory.getApplicationConnectors().size() > 0) {
                final ConnectorFactory connectorFactory = defaultServerFactory.getApplicationConnectors().get(0);

                final UriConfig uriConfig = new NodeUriConfig();
                // Allow explicit configuration of the host/port in case they differ from what will be discovered,
                // e.g. if running inside a docker container, or there is some sort of port mapping going on.
                uriConfig.setHostname(appConfig.getNodeUri().getHostname());
                uriConfig.setPort(appConfig.getNodeUri().getPort());

                if (connectorFactory instanceof HttpsConnectorFactory) {
                    final HttpsConnectorFactory httpsConnectorFactory = (HttpsConnectorFactory) connectorFactory;
                    uriConfig.setScheme("https");
                    if (uriConfig.getHostname() == null) {
                        uriConfig.setHostname(resolveHost(httpsConnectorFactory.getBindHost()));
                    }
                    if (uriConfig.getPort() == null) {
                        uriConfig.setPort(httpsConnectorFactory.getPort());
                    }

                } else if (connectorFactory instanceof HttpConnectorFactory) {
                    final HttpConnectorFactory httpConnectorFactory = (HttpConnectorFactory) connectorFactory;
                    uriConfig.setScheme("http");
                    if (uriConfig.getHostname() == null) {
                        uriConfig.setHostname(resolveHost(httpConnectorFactory.getBindHost()));
                    }
                    if (uriConfig.getPort() == null) {
                        uriConfig.setPort(httpConnectorFactory.getPort());
                    }
                }
                uriConfig.setPathPrefix(defaultServerFactory.getApplicationContextPath());
                localBaseUri = uriConfig.toString();
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
