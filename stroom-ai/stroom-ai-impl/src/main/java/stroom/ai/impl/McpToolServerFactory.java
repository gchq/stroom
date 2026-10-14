/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.ai.impl;

import stroom.credentials.api.HttpConfigResolver;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.openai.shared.McpToolServerConfig;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.util.cert.SSLConfig;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.SsrfGuard;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.util.shared.time.SimpleDuration;

import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.resourcesastools.DefaultMcpResourcesAsToolsPresenter;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Builds the {@link McpToolset} for an {@link OpenAIModelDoc} — one MCP client per enabled tool server,
 * wrapped in a {@link McpToolProvider} that also exposes each server's MCP resources as tools.
 * <p>
 * Connections use the MCP <em>Streamable HTTP</em> transport (JDK {@link java.net.http.HttpClient})
 * pinned to HTTP/1.1, and support both {@code http://} and {@code https://}. Of the tool server's
 * {@link HttpClientConfig} only the response timeout and redirect handling are honoured for every
 * endpoint, plus the TLS settings for {@code https://} endpoints; other settings do not map onto that
 * transport.
 */
@Singleton
public class McpToolServerFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpToolServerFactory.class);

    private static final SimpleDuration DEFAULT_TIMEOUT = SimpleDuration
            .builder()
            .time(2)
            .timeUnit(stroom.util.shared.time.TimeUnit.MINUTES)
            .build();

    private final Provider<StoredSecrets> storedSecretsProvider;
    private final HttpConfigResolver httpConfigResolver;
    private final PathCreator pathCreator;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<UserIdentityFactory> userIdentityFactoryProvider;

    @Inject
    McpToolServerFactory(final Provider<StoredSecrets> storedSecretsProvider,
                         final HttpConfigResolver httpConfigResolver,
                         final PathCreator pathCreator,
                         final Provider<SecurityContext> securityContextProvider,
                         final Provider<UserIdentityFactory> userIdentityFactoryProvider) {
        this.storedSecretsProvider = storedSecretsProvider;
        this.httpConfigResolver = httpConfigResolver;
        this.pathCreator = pathCreator;
        this.securityContextProvider = securityContextProvider;
        this.userIdentityFactoryProvider = userIdentityFactoryProvider;
    }

    /**
     * @return a toolset for every enabled MCP tool server on the model, or {@link McpToolset#EMPTY} if
     * there are none. The caller must {@link McpToolset#close() close} the returned toolset.
     */
    public McpToolset createToolset(final OpenAIModelDoc modelDoc) {
        final List<McpToolServerConfig> servers = NullSafe.list(modelDoc.getMcpToolServers()).stream()
                .filter(McpToolServerConfig::isEnabled)
                .filter(s -> NullSafe.isNonBlankString(s.getEndpoint()))
                .toList();
        if (servers.isEmpty()) {
            return McpToolset.EMPTY;
        }

        final boolean debug = LOGGER.isDebugEnabled();
        final List<McpClient> clients = new ArrayList<>();
        try {
            for (final McpToolServerConfig server : servers) {
                clients.add(createClient(server, debug));
            }

            final McpToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(clients)
                    .failIfOneServerFails(false)
                    .resourcesAsToolsPresenter(DefaultMcpResourcesAsToolsPresenter.builder().build())
                    .build();
            return new McpToolset(clients, toolProvider);

        } catch (final RuntimeException e) {
            // Do not leak connections if one server fails to start.
            for (final McpClient client : clients) {
                try {
                    client.close();
                } catch (final Exception closeError) {
                    LOGGER.debug(() -> "Failed to close MCP client after a build failure", closeError);
                }
            }
            throw e;
        }
    }

    private McpClient createClient(final McpToolServerConfig server, final boolean debug) {
        final String endpoint = server.getEndpoint().trim();
        final String label = NullSafe.nonBlankStringElse(server.getName(), endpoint);

        // Reject cloud-metadata / wildcard targets. Loopback and private addresses are allowed, since a
        // self-hosted MCP server legitimately lives there.
        SsrfGuard.rejectMetadataAndWildcard(endpoint);

        final HttpClientConfig httpClientConfig = Objects.requireNonNullElseGet(
                server.getHttpClientConfiguration(),
                () -> HttpClientConfig.builder().build());
        final Duration timeout = toDuration(httpClientConfig.getTimeout());
        final boolean secure = isHttps(endpoint);

        final StreamableHttpMcpTransport.Builder transportBuilder = StreamableHttpMcpTransport.builder()
                .url(endpoint)
                .timeout(timeout)
                .followRedirects(httpClientConfig.isFollowRedirects())
                .customHeaders(buildAuthHeaders(server, label))
                .logRequests(debug)
                .logResponses(debug)
                // The JDK HTTP client defaults to HTTP/2, which for a cleartext http:// URL means an
                // HTTP/2 prior-knowledge connection that an HTTP/1.1-only server rejects. MCP servers do
                // not require HTTP/2, so pin 1.1 for interoperability (matches the OpenAI model client).
                .setHttpVersion1_1();

        // TLS settings only apply to https:// endpoints. For http:// there is nothing to configure, and
        // building an SSLContext there would be pointless (and its config is quietly ignored).
        if (secure) {
            final SSLContext sslContext = buildSslContext(httpClientConfig, label);
            if (sslContext != null) {
                transportBuilder.sslContext(sslContext);
            }
        } else {
            warnIfTlsConfigured(httpClientConfig, label);
        }

        final McpTransport transport = transportBuilder.build();

        try {
            return DefaultMcpClient.builder()
                    .transport(transport)
                    .key(label)
                    .clientName("Stroom")
                    .initializationTimeout(timeout)
                    .toolExecutionTimeout(timeout)
                    .resourcesTimeout(timeout)
                    .build();
        } catch (final RuntimeException e) {
            throw new RuntimeException("MCP tool server '" + label + "' could not be reached at "
                                       + endpoint + " - "
                                       + NullSafe.nonBlankStringElse(e.getMessage(), e.toString()), e);
        }
    }

    // ------------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------------

    Map<String, String> buildAuthHeaders(final McpToolServerConfig server, final String label) {
        final Map<String, String> headers = new LinkedHashMap<>();
        switch (server.getAuthMode()) {
            case NONE -> {
                // no header
            }
            case BEARER_TOKEN -> headers.put("Authorization", "Bearer " + requireAccessToken(server, label));
            case API_KEY -> {
                final String headerName = NullSafe.nonBlankStringElse(
                        server.getApiKeyHeaderName(), McpToolServerConfig.DEFAULT_API_KEY_HEADER_NAME);
                final String prefix = NullSafe.string(server.getApiKeyValuePrefix());
                headers.put(headerName, prefix + requireAccessToken(server, label));
            }
            case BASIC -> {
                final UsernamePasswordSecret secret = requireSecret(
                        server, label, UsernamePasswordSecret.class);
                final String raw = NullSafe.string(secret.getUsername()) + ":" + NullSafe.string(secret.getPassword());
                headers.put("Authorization", "Basic " + Base64.getEncoder()
                        .encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
            }
            case STROOM_SESSION_TOKEN -> headers.putAll(sessionAuthHeaders(label));
        }
        return headers;
    }

    /**
     * The {@code Authorization} header(s) Stroom uses for outbound calls as the current user, i.e. the
     * OAuth2 token from the user's session passed straight through to the MCP server.
     */
    private Map<String, String> sessionAuthHeaders(final String label) {
        final UserIdentity userIdentity = securityContextProvider.get().getUserIdentity();
        if (userIdentity == null) {
            throw new RuntimeException("MCP tool server '" + label
                                       + "': there is no authenticated Stroom user to pass a token through for");
        }
        final Map<String, String> authHeaders = userIdentityFactoryProvider.get().getAuthHeaders(userIdentity);
        if (NullSafe.isEmptyMap(authHeaders)) {
            throw new RuntimeException("MCP tool server '" + label
                                       + "': the current Stroom session has no OAuth2 token to pass through "
                                       + "(is Stroom using an external identity provider?)");
        }
        return authHeaders;
    }

    private String requireAccessToken(final McpToolServerConfig server, final String label) {
        final AccessTokenSecret secret = requireSecret(server, label, AccessTokenSecret.class);
        if (NullSafe.isBlankString(secret.getAccessToken())) {
            throw new RuntimeException("MCP tool server '" + label + "': credential '"
                                       + server.getCredentialName() + "' has no access token");
        }
        return secret.getAccessToken();
    }

    private <T> T requireSecret(final McpToolServerConfig server, final String label, final Class<T> type) {
        if (NullSafe.isBlankString(server.getCredentialName())) {
            throw new RuntimeException("MCP tool server '" + label + "': authentication mode "
                                       + server.getAuthMode().getDisplayValue() + " needs a credential to be set");
        }
        final StoredSecret storedSecret = storedSecretsProvider.get().get(server.getCredentialName());
        if (storedSecret == null || storedSecret.secret() == null) {
            throw new RuntimeException("MCP tool server '" + label + "': credential '"
                                       + server.getCredentialName() + "' cannot be found");
        }
        if (!type.isInstance(storedSecret.secret())) {
            throw new RuntimeException("MCP tool server '" + label + "': credential '"
                                       + server.getCredentialName() + "' is the wrong type for authentication mode "
                                       + server.getAuthMode().getDisplayValue());
        }
        return type.cast(storedSecret.secret());
    }

    // ------------------------------------------------------------------------
    // TLS
    // ------------------------------------------------------------------------

    private static boolean isHttps(final String endpoint) {
        try {
            return "https".equalsIgnoreCase(URI.create(endpoint).getScheme());
        } catch (final RuntimeException e) {
            // A malformed URL is caught later when the transport fails to connect; assume plain http here.
            return false;
        }
    }

    private void warnIfTlsConfigured(final HttpClientConfig httpClientConfig, final String label) {
        final HttpTlsConfig tls = httpClientConfig.getTls();
        if (tls != null
            && (NullSafe.isNonBlankString(tls.getKeyStoreName())
                || NullSafe.isNonBlankString(tls.getTrustStoreName())
                || tls.isTrustSelfSignedCertificates())) {
            LOGGER.warn(() -> "MCP tool server '" + label + "': TLS settings are configured but the endpoint "
                              + "is a plain http:// URL, so they will be ignored.");
        }
    }

    /**
     * @return an {@link SSLContext} when the TLS config needs one (custom trust/key store or
     * trust-self-signed), otherwise null so the JDK default context is used.
     */
    private SSLContext buildSslContext(final HttpClientConfig httpClientConfig, final String label) {
        final HttpTlsConfiguration tls = httpConfigResolver.resolve(httpClientConfig.getTls());
        if (tls == null) {
            return null;
        }

        final boolean hasKeyStore = NullSafe.isNonBlankString(tls.getKeyStorePath());
        final boolean hasTrustStore = NullSafe.isNonBlankString(tls.getTrustStorePath());
        final boolean trustAll = tls.isTrustSelfSignedCertificates();
        if (!hasKeyStore && !hasTrustStore && !trustAll) {
            return null;
        }

        if (!tls.isVerifyHostname()) {
            LOGGER.warn(() -> "MCP tool server '" + label + "': 'verify hostname' is turned off in the TLS "
                              + "configuration, but that cannot be honoured for MCP connections and will be "
                              + "ignored.");
        }

        try {
            final SSLConfig sslConfig = SSLConfig.builder()
                    .withSslProtocol(NullSafe.nonBlankStringElse(tls.getProtocol(), "TLSv1.2"))
                    .withKeyStorePath(tls.getKeyStorePath())
                    .withKeyStorePassword(tls.getKeyStorePassword())
                    .withKeyStoreType(tls.getKeyStoreType())
                    .withTrustStorePath(tls.getTrustStorePath())
                    .withTrustStorePassword(tls.getTrustStorePassword())
                    .withTrustStoreType(tls.getTrustStoreType())
                    .build();

            final KeyManager[] keyManagers = hasKeyStore
                    ? SSLUtil.createKeyManagers(sslConfig, pathCreator)
                    : null;
            final TrustManager[] trustManagers = trustAll
                    ? new TrustManager[]{TRUST_ALL}
                    : (hasTrustStore
                            ? SSLUtil.createTrustManagers(sslConfig, pathCreator)
                            : null);

            final SSLContext sslContext = SSLContext.getInstance(sslConfig.getSslProtocol());
            sslContext.init(keyManagers, trustManagers, new SecureRandom());
            return sslContext;
        } catch (final NoSuchAlgorithmException | KeyManagementException e) {
            throw new RuntimeException("MCP tool server '" + label + "': failed to build TLS context - "
                                       + e.getMessage(), e);
        }
    }

    private static final X509TrustManager TRUST_ALL = new X509TrustManager() {
        @Override
        public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
        }

        @Override
        public void checkServerTrusted(final X509Certificate[] chain, final String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

    private static Duration toDuration(final SimpleDuration simpleDuration) {
        final SimpleDuration effective = simpleDuration != null
                ? simpleDuration
                : DEFAULT_TIMEOUT;
        return stroom.util.time.SimpleDurationUtil.convertToStroomDuration(effective).getDuration();
    }
}
