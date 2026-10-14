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

import stroom.ai.impl.McpToolset.McpServerConnection;
import stroom.credentials.api.HttpConfigResolver;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.openai.shared.McpAuthMode;
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

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;
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
import java.util.function.Supplier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Builds the {@link McpToolset} for an {@link OpenAIModelDoc}: one MCP Java SDK client per enabled tool
 * server.
 * <p>
 * Connections use the MCP <em>Streamable HTTP</em> transport (JDK {@link java.net.http.HttpClient})
 * pinned to HTTP/1.1, and support both {@code http://} and {@code https://}. Of the tool server's
 * {@link HttpClientConfig} only the timeout and redirect handling are honoured for every endpoint, plus
 * the TLS settings for {@code https://} endpoints; other settings do not map onto that transport.
 * <p>
 * Authentication headers are added to every request. For the Stroom session token mode the user's
 * identity is captured when the toolset is built, and a current token is asked for on each request, so a
 * token that is refreshed during a long run is picked up. This is needed because requests are sent from
 * the HTTP client's own threads, which do not carry the user's security context.
 */
@Singleton
public class McpToolServerFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(McpToolServerFactory.class);

    private static final SimpleDuration DEFAULT_TIMEOUT = SimpleDuration
            .builder()
            .time(2)
            .timeUnit(stroom.util.shared.time.TimeUnit.MINUTES)
            .build();

    private static final String CLIENT_NAME = "Stroom";

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

        final List<McpServerConnection> connections = new ArrayList<>();
        try {
            for (final McpToolServerConfig server : servers) {
                connections.add(connect(server));
            }
            return new McpToolset(connections);

        } catch (final RuntimeException e) {
            // Do not leak connections if one server fails to start.
            new McpToolset(connections).close();
            throw e;
        }
    }

    private McpServerConnection connect(final McpToolServerConfig server) {
        final String endpoint = server.getEndpoint().trim();
        final String label = NullSafe.nonBlankStringElse(server.getName(), endpoint);

        // Reject cloud-metadata / wildcard targets. Loopback and private addresses are allowed, since a
        // self-hosted MCP server legitimately lives there.
        SsrfGuard.rejectMetadataAndWildcard(endpoint);

        final HttpClientConfig httpClientConfig = Objects.requireNonNullElseGet(
                server.getHttpClientConfiguration(),
                () -> HttpClientConfig.builder().build());
        final Duration timeout = toDuration(httpClientConfig.getTimeout());
        final SSLContext sslContext = isHttps(endpoint)
                ? buildSslContext(httpClientConfig, label)
                : null;
        if (!isHttps(endpoint)) {
            warnIfTlsConfigured(httpClientConfig, label);
        }
        final Supplier<Map<String, String>> authHeaders = authHeadersSupplier(server, label);

        final HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(endpoint)
                .connectTimeout(timeout)
                .customizeClient(clientBuilder -> {
                    // The JDK HTTP client defaults to HTTP/2, which for a cleartext http:// URL means an
                    // HTTP/2 prior-knowledge connection that an HTTP/1.1-only server rejects. MCP servers do
                    // not require HTTP/2, so pin 1.1 for interoperability (matches the OpenAI model client).
                    clientBuilder.version(HttpClient.Version.HTTP_1_1);
                    clientBuilder.followRedirects(httpClientConfig.isFollowRedirects()
                            ? HttpClient.Redirect.NORMAL
                            : HttpClient.Redirect.NEVER);
                    if (sslContext != null) {
                        clientBuilder.sslContext(sslContext);
                    }
                })
                .httpRequestCustomizer((requestBuilder, method, uri, body, context) ->
                        authHeaders.get().forEach(requestBuilder::setHeader))
                .build();

        final McpSyncClient client = McpClient.sync(transport)
                .clientInfo(new Implementation(CLIENT_NAME, CLIENT_NAME, clientVersion()))
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .build();
        try {
            client.initialize();
        } catch (final RuntimeException e) {
            closeQuietly(client, label);
            throw new RuntimeException("MCP tool server '" + label + "' could not be reached at "
                                       + endpoint + " - "
                                       + NullSafe.nonBlankStringElse(e.getMessage(), e.toString()), e);
        }
        LOGGER.debug(() -> "Connected to MCP tool server '" + label + "' at " + endpoint);
        return new McpServerConnection(label, server, client);
    }

    private static void closeQuietly(final McpSyncClient client, final String label) {
        try {
            client.close();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Failed to close MCP client '" + label + "'", e);
        }
    }

    private static String clientVersion() {
        return NullSafe.nonBlankStringElse(McpToolServerFactory.class.getPackage().getImplementationVersion(),
                "unknown");
    }

    // ------------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------------

    /**
     * @return The authentication headers to send to the server, as they stand now.
     */
    Map<String, String> buildAuthHeaders(final McpToolServerConfig server, final String label) {
        return authHeadersSupplier(server, label).get();
    }

    /**
     * Resolves the server's credentials now, so that a missing or wrong credential is reported straight
     * away, and returns a supplier of the headers to add to each request.
     */
    private Supplier<Map<String, String>> authHeadersSupplier(final McpToolServerConfig server, final String label) {
        if (server.getAuthMode() == McpAuthMode.STROOM_SESSION_TOKEN) {
            final UserIdentity userIdentity = securityContextProvider.get().getUserIdentity();
            if (userIdentity == null) {
                throw new RuntimeException("MCP tool server '" + label
                                           + "': there is no authenticated Stroom user to pass a token through for");
            }
            // Check there is a token now, then ask for a current one on every request.
            sessionAuthHeaders(userIdentity, label);
            return () -> sessionAuthHeaders(userIdentity, label);
        }

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
            default -> throw new IllegalStateException("Unexpected auth mode " + server.getAuthMode());
        }
        final Map<String, String> fixedHeaders = Map.copyOf(headers);
        return () -> fixedHeaders;
    }

    /**
     * The {@code Authorization} header(s) Stroom uses for outbound calls as the user, i.e. the OAuth2 token
     * from the user's session passed straight through to the MCP server.
     */
    private Map<String, String> sessionAuthHeaders(final UserIdentity userIdentity, final String label) {
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
