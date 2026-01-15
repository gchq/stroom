/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.app.uri;

import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.common.NodeUriConfig;
import stroom.config.common.PublicUriConfig;
import stroom.config.common.UiUriConfig;
import stroom.config.common.UriFactory;
import stroom.util.net.UriConfig;
import stroom.util.shared.NullSafe;

import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.jetty.HttpsConnectorFactory;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

@Singleton
class UriFactoryImpl implements UriFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(UriFactoryImpl.class);

    private final Provider<AppConfig> appConfigProvider;
    private final Config config;

    private String localBaseUri;
    private String publicBaseUri;
    private String uiBaseUri;

    @Inject
    UriFactoryImpl(final Config config,
                   final Provider<AppConfig> appConfigProvider) {
        this.config = config;
        this.appConfigProvider = appConfigProvider;

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
            final UiUriConfig uiUriConfig = appConfigProvider.get().getUiUri();
            if (isValid(uiUriConfig)) {
                uiBaseUri = uiUriConfig.toString();
            } else {
                uiBaseUri = getPublicBaseUri();
            }
        }
        return uiBaseUri;
    }

    private String getPublicBaseUri() {
        if (publicBaseUri == null) {
            final PublicUriConfig publicUriConfig = appConfigProvider.get().getPublicUri();
            if (isValid(publicUriConfig)) {
                publicBaseUri = publicUriConfig.toString();
            } else {
                publicBaseUri = getLocalBaseUri();
            }
        }
        return publicBaseUri;
    }

    private String getLocalBaseUri() {
        if (localBaseUri == null) {
            final NodeUriConfig nodeUriConfig = appConfigProvider.get().getNodeUri();
            if (isValid(nodeUriConfig)) {
                localBaseUri = nodeUriConfig.toString();
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

                NodeUriConfig uriConfig = new NodeUriConfig();
                // Allow explicit configuration of the host/port in case they differ from what will be discovered,
                // e.g. if running inside a docker container, or there is some sort of port mapping going on.
                final AppConfig appConfig = appConfigProvider.get();
                uriConfig = uriConfig.withHostname(appConfig.getNodeUri().getHostname());
                if (appConfig.getNodeUri().getPort() != null) {
                    uriConfig = uriConfig.withPort(appConfig.getNodeUri().getPort());
                }

                if (connectorFactory instanceof HttpsConnectorFactory) {
                    final HttpsConnectorFactory httpsConnectorFactory = (HttpsConnectorFactory) connectorFactory;
                    uriConfig = uriConfig.withScheme("https");
                    if (uriConfig.getHostname() == null) {
                        uriConfig = uriConfig.withHostname(resolveHost(httpsConnectorFactory.getBindHost()));
                    }
                    if (uriConfig.getPort() == null) {
                        uriConfig = uriConfig.withPort(httpsConnectorFactory.getPort());
                    }

                } else if (connectorFactory instanceof HttpConnectorFactory) {
                    final HttpConnectorFactory httpConnectorFactory = (HttpConnectorFactory) connectorFactory;
                    uriConfig = uriConfig.withScheme("http");
                    if (uriConfig.getHostname() == null) {
                        uriConfig = uriConfig.withHostname(resolveHost(httpConnectorFactory.getBindHost()));
                    }
                    if (uriConfig.getPort() == null) {
                        uriConfig = uriConfig.withPort(httpConnectorFactory.getPort());
                    }
                }
                uriConfig = uriConfig.withPathPrefix(defaultServerFactory.getApplicationContextPath());
                localBaseUri = uriConfig.toString();
            }
        }
        return localBaseUri;
    }

    private String resolveHost(final String bindHost) {
        return NullSafe.isNonEmptyString(bindHost)
                ? bindHost
                : "localhost";
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
        return uriConfig.getHostname() != null && !uriConfig.getHostname().isEmpty();
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

        if (path != null && !path.isEmpty() && !path.equals("/")) {
            if (!path.startsWith("/")) {
                sb.append("/");
            }
            sb.append(path);
        }
        return sb.toString();
    }
}
