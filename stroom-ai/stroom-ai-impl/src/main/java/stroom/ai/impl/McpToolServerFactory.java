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
import stroom.openai.shared.McpAuthMode;
import stroom.openai.shared.McpToolServerConfig;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.HasJwt;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.UserTokenIssuer;
import stroom.util.cert.SSLConfig;
import stroom.util.http.HttpClientUtil;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.SsrfGuard;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.util.shared.time.SimpleDuration;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Builds the {@link McpToolset} for an {@link OpenAIModelDoc}: one MCP Java SDK client per enabled tool
 * server.
 * <p>
 * Connections use the MCP <em>Streamable HTTP</em> transport (JDK {@link java.net.http.HttpClient})
 * pinned to HTTP/1.1, and support both {@code http://} and {@code https://}. Of the tool server's
 * {@link HttpClientConfig} only the timeout, redirect handling, user agent and headers are honoured for every
 * endpoint, plus the TLS settings for {@code https://} endpoints; other settings do not map onto that
 * transport.
 * <p>
 * Authentication headers are added to every request, after the configured headers, so they win. For the
 * Stroom session token mode the user's identity is captured when the toolset is built, and a current token is
 * asked for on each request, so a token that is refreshed during a long run is picked up. This is needed
 * because requests are sent from the HTTP client's own threads, which do not carry the user's security
 * context.
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
    // How much longer than a form's timeout a tool call may take, so the form expires first.
    private static final Duration FORM_TIMEOUT_MARGIN = Duration.ofMinutes(1);

    private final Provider<StoredSecrets> storedSecretsProvider;
    private final HttpConfigResolver httpConfigResolver;
    private final PathCreator pathCreator;
    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<UserIdentityFactory> userIdentityFactoryProvider;
    private final Provider<McpElicitations> elicitationsProvider;
    private final Provider<AiConfig> aiConfigProvider;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final Provider<UserTokenIssuer> userTokenIssuerProvider;
    private final McpClientCache mcpClientCache;

    @Inject
    McpToolServerFactory(final Provider<StoredSecrets> storedSecretsProvider,
                         final HttpConfigResolver httpConfigResolver,
                         final PathCreator pathCreator,
                         final Provider<SecurityContext> securityContextProvider,
                         final Provider<UserIdentityFactory> userIdentityFactoryProvider,
                         final McpClientCache mcpClientCache,
                         final Provider<McpElicitations> elicitationsProvider,
                         final Provider<AiConfig> aiConfigProvider,
                         final Provider<OpenIdConfiguration> openIdConfigurationProvider,
                         final Provider<UserTokenIssuer> userTokenIssuerProvider) {
        this.storedSecretsProvider = storedSecretsProvider;
        this.userTokenIssuerProvider = userTokenIssuerProvider;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.elicitationsProvider = elicitationsProvider;
        this.aiConfigProvider = aiConfigProvider;
        this.httpConfigResolver = httpConfigResolver;
        this.pathCreator = pathCreator;
        this.securityContextProvider = securityContextProvider;
        this.userIdentityFactoryProvider = userIdentityFactoryProvider;
        this.mcpClientCache = mcpClientCache;
    }

    /**
     * Connects to every enabled MCP tool server on the model, for one question that is not part of a chat.
     * A server that cannot be reached is left out and reported in {@link McpToolset#getWarnings()}.
     *
     * @return a toolset owning its connections, or {@link McpToolset#EMPTY} if there are no servers. The
     * caller must {@link McpToolset#close() close} the returned toolset.
     */
    public McpToolset createToolset(final OpenAIModelDoc modelDoc) {
        return createToolset(modelDoc, null);
    }

    /**
     * Gets connections to every enabled MCP tool server on the model for a question in a chat. Connections
     * are reused from earlier questions in the chat where possible (see {@link McpClientCache}). A server that
     * cannot be reached is left out and reported in {@link McpToolset#getWarnings()}.
     *
     * @param modelDoc The model, with its tool servers.
     * @param chatId   The chat, or null to make connections just for this question.
     * @return the toolset, or {@link McpToolset#EMPTY} if there are no servers. The caller must
     * {@link McpToolset#close() close} the returned toolset.
     */
    public McpToolset createToolset(final OpenAIModelDoc modelDoc, final Integer chatId) {
        final List<McpToolServerConfig> servers = NullSafe.list(modelDoc.getMcpToolServers()).stream()
                .filter(McpToolServerConfig::isEnabled)
                .filter(s -> NullSafe.isNonBlankString(s.getEndpoint()))
                .toList();
        if (servers.isEmpty()) {
            return McpToolset.EMPTY;
        }

        final UserIdentity userIdentity = securityContextProvider.get().getUserIdentity();
        final String userUuid = NullSafe.get(securityContextProvider.get().getUserRef(), UserRef::getUuid);
        final List<McpServerConnection> connections = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        for (final McpToolServerConfig server : servers) {
            try {
                final McpServerConnection connection = chatId == null
                        ? connect(server, userIdentity, null)
                        : mcpClientCache.get(chatId, userUuid, server, () -> connect(server, userIdentity, chatId));
                connection.use(userIdentity);
                connections.add(connection);
            } catch (final RuntimeException e) {
                // One server being down must not stop the question being answered with the others.
                LOGGER.debug(() -> "Leaving out MCP tool server '" + server.getName() + "'", e);
                warnings.add(NullSafe.nonBlankStringElse(e.getMessage(), e.toString()));
            }
        }
        return new McpToolset(connections, warnings, chatId == null);
    }

    /**
     * @param chatId The chat the connection is for, or null. Only a chat's connection can show the user the
     *               forms its server asks them to fill in (MCP elicitation).
     */
    private McpServerConnection connect(final McpToolServerConfig server,
                                        final UserIdentity userIdentity,
                                        final Integer chatId) {
        final String endpoint = server.getEndpoint().trim();
        final String label = NullSafe.nonBlankStringElse(server.getName(), endpoint);

        // Reject cloud-metadata / wildcard targets. Loopback and private addresses are allowed, since a
        // self-hosted MCP server legitimately lives there.
        try {
            SsrfGuard.rejectMetadataAndWildcard(endpoint);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("MCP tool server '" + label + "' is not allowed - "
                                               + e.getMessage(), e);
        }

        final HttpClientConfig httpClientConfig = Objects.requireNonNullElseGet(
                server.getHttpClientConfiguration(),
                () -> HttpClientConfig.builder().build());
        final Duration timeout = toDuration(httpClientConfig.getTimeout());
        // How long a tool call may take. In a chat, one waits while the user fills in a form, so it must not time
        // out first.
        final Duration callTimeout = chatId != null
                ? longer(timeout, formTimeout())
                : timeout;
        final SSLContext sslContext = isHttps(endpoint)
                ? buildSslContext(httpClientConfig, label)
                : null;
        if (!isHttps(endpoint)) {
            warnIfTlsConfigured(httpClientConfig, label);
        }
        final AtomicReference<UserIdentity> userIdentityRef = new AtomicReference<>(userIdentity);
        final Supplier<Map<String, String>> authHeaders = authHeadersSupplier(server, label, userIdentityRef,
                callTimeout);
        final Map<String, String> configuredHeaders = configuredHeaders(httpClientConfig);

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
                .httpRequestCustomizer((requestBuilder, method, uri, body, context) -> {
                    // The configured headers first, so that the authentication headers win.
                    configuredHeaders.forEach((name, value) -> setHeader(requestBuilder, name, value, label));
                    authHeaders.get().forEach(requestBuilder::setHeader);
                })
                .build();

        // The connection is made after the client, but the client needs to tell it when the tools change.
        final AtomicReference<McpServerConnection> connectionRef = new AtomicReference<>();
        final McpClient.SyncSpec spec = McpClient.sync(transport)
                .clientInfo(new Implementation(CLIENT_NAME, CLIENT_NAME, clientVersion()))
                .requestTimeout(timeout)
                .initializationTimeout(timeout)
                .toolsChangeConsumer(tools -> NullSafe.consume(connectionRef.get(),
                        McpServerConnection::invalidateTools))
                // What a tool call has got to, for the calls that asked to be told (see McpToolset).
                .progressConsumer(notification -> NullSafe.consume(connectionRef.get(),
                        connection -> connection.onProgress(notification)));
        if (chatId != null) {
            final McpElicitations elicitations = elicitationsProvider.get();
            spec.capabilities(ClientCapabilities.builder().elicitation().build())
                    .requestTimeout(callTimeout)
                    .elicitation(request -> elicitations.handle(chatId, label, request));
        }
        final McpSyncClient client = spec.build();
        try {
            client.initialize();
        } catch (final RuntimeException e) {
            closeQuietly(client, label);
            LOGGER.debug(() -> "Unable to connect to MCP tool server '" + label + "' at " + endpoint, e);
            throw new RuntimeException("MCP tool server '" + label + "' could not be reached at "
                                       + endpoint + " - " + describeFailure(e), e);
        }
        LOGGER.debug(() -> "Connected to MCP tool server '" + label + "' at " + endpoint);
        final McpServerConnection connection = new McpServerConnection(label, server, client, userIdentityRef);
        connectionRef.set(connection);
        return connection;
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

    /**
     * @return The headers the configuration says to send: its User-Agent, if it has one, then its headers.
     */
    static Map<String, String> configuredHeaders(final HttpClientConfig httpClientConfig) {
        final Map<String, String> headers = new LinkedHashMap<>();
        if (NullSafe.isNonBlankString(httpClientConfig.getUserAgent())) {
            headers.put("User-Agent", httpClientConfig.getUserAgent().strip());
        }
        headers.putAll(HttpClientUtil.headers(httpClientConfig));
        return headers;
    }

    /**
     * Sets a configured header, leaving out any the JDK HTTP client does not allow to be set (e.g. 'Host'),
     * rather than failing every request.
     */
    private static void setHeader(final HttpRequest.Builder requestBuilder,
                                  final String name,
                                  final String value,
                                  final String label) {
        try {
            requestBuilder.setHeader(name, value);
        } catch (final IllegalArgumentException e) {
            LOGGER.debug(() -> "Not sending header '" + name + "' to MCP tool server '" + label + "': "
                               + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Authentication
    // ------------------------------------------------------------------------

    /**
     * @return The authentication headers to send to the server, as they stand now.
     */
    Map<String, String> buildAuthHeaders(final McpToolServerConfig server, final String label) {
        return authHeadersSupplier(server, label,
                new AtomicReference<>(securityContextProvider.get().getUserIdentity()),
                toDuration(NullSafe.get(server.getHttpClientConfiguration(), HttpClientConfig::getTimeout))).get();
    }

    /// @return How long a tool call that waits for the user to fill in a form may take: the form's timeout, and a
    /// little more, so the form expires first.
    private Duration formTimeout() {
        return NullSafe.getOrElse(aiConfigProvider.get(), AiConfig::getElicitationTimeout,
                AiConfig.DEFAULT_ELICITATION_TIMEOUT).getDuration().plus(FORM_TIMEOUT_MARGIN);
    }

    private static Duration longer(final Duration one, final Duration other) {
        return one.compareTo(other) > 0
                ? one
                : other;
    }

    /**
     * Resolves the server's credentials now, so that a missing or wrong credential is reported straight
     * away, and returns a supplier of the headers to add to each request.
     *
     * @param callTimeout How long a request may take, which a token Stroom issues must outlast, as the server
     *                    keeps using the token a request came with until it answers.
     */
    private Supplier<Map<String, String>> authHeadersSupplier(final McpToolServerConfig server,
                                                              final String label,
                                                              final AtomicReference<UserIdentity> userIdentity,
                                                              final Duration callTimeout) {
        if (server.getAuthMode() == McpAuthMode.STROOM_SESSION_TOKEN
            && NullSafe.get(openIdConfigurationProvider.get(), OpenIdConfiguration::getIdentityProviderType)
               == IdpType.INTERNAL_IDP) {
            return issuedTokenHeadersSupplier(server, label, userIdentity, callTimeout);
        }
        if (server.getAuthMode() == McpAuthMode.STROOM_SESSION_TOKEN) {
            // Only an external identity provider gives Stroom a token another server can check (U3).
            if (NullSafe.get(openIdConfigurationProvider.get(), OpenIdConfiguration::getIdentityProviderType)
                != IdpType.EXTERNAL_IDP) {
                throw new RuntimeException("The tools of MCP tool server '" + label + "' are unavailable: passing "
                                           + "your sign-in through to it needs Stroom to use an external "
                                           + "identity provider. Ask an administrator. Built-in tools still "
                                           + "work.");
            }
            if (userIdentity.get() == null) {
                throw new RuntimeException("MCP tool server '" + label
                                           + "': there is no authenticated Stroom user to pass a token through for");
            }
            // Check there is a token now, then ask for a current one on every request, for whichever user is
            // using the connection at the time.
            sessionAuthHeaders(userIdentity.get(), label);
            return () -> sessionAuthHeaders(userIdentity.get(), label);
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

    /// @return What went wrong connecting, in words: the underlying cause (the client library only says that it
    /// failed to initialise), with a hint for the usual causes.
    static String describeFailure(final Throwable e) {
        final List<String> messages = new ArrayList<>();
        Throwable cause = e;
        Throwable root = e;
        while (cause != null && messages.size() < 5) {
            final String message = NullSafe.nonBlankStringElse(cause.getMessage(), cause.getClass().getSimpleName());
            // Skipped if an outer message already includes it, as wrappers often repeat their cause.
            if (messages.stream().noneMatch(outer -> outer.contains(message))) {
                messages.add(message);
            }
            root = cause;
            cause = cause.getCause() == cause
                    ? null
                    : cause.getCause();
        }
        final String text = String.join(": ", messages);
        final String lower = (text + " " + root.getClass().getName()).toLowerCase(Locale.ROOT);
        final String hint;
        if (lower.contains("401") || lower.contains("403") || lower.contains("unauthori")
            || lower.contains("forbidden")) {
            hint = "The server refused Stroom's credentials. Check the authentication settings; for OAuth2 "
                   + "passthrough with Stroom's internal identity provider, check the server accepts Stroom's "
                   + "issuer, JWKS URL and the token audience.";
        } else if (lower.contains("ssl") || lower.contains("certificate") || lower.contains("pkix")
                   || lower.contains("handshake")) {
            hint = "The secure connection failed. Check the server's certificate is trusted, e.g. with the "
                   + "trust store in the HTTP client config (TLS tab).";
        } else if (lower.contains("unresolvedaddress") || lower.contains("unknownhost")
                   || lower.contains("name or service not known")) {
            hint = "The server's host name could not be found from this Stroom node.";
        } else if (lower.contains("connection refused") || lower.contains("connectexception")) {
            hint = "Nothing is listening at that address; check the URL and port, and that the server is running.";
        } else if (lower.contains("timeout") || lower.contains("timed out")) {
            hint = "The server didn't answer in time; check it is reachable from this Stroom node.";
        } else if (lower.contains("404")) {
            hint = "Nothing was found at that path; check the endpoint URL (FastMCP's is usually /mcp).";
        } else {
            hint = null;
        }
        return hint == null
                ? text
                : text + ". " + hint;
    }

    /// For 'OAuth2 passthrough' when Stroom is its own identity provider: Stroom has no sign-in token another server
    /// can check, so it issues each user a short-lived token for the server's audience instead, which the server
    /// checks against Stroom's issuer and keys. As the token lets the server act as the user, it is only sent to a
    /// trusted server, over https or on the same machine.
    private Supplier<Map<String, String>> issuedTokenHeadersSupplier(final McpToolServerConfig server,
                                                                     final String label,
                                                                     final AtomicReference<UserIdentity> userIdentity,
                                                                     final Duration callTimeout) {
        final String audience = NullSafe.trim(server.getAudience());
        if (audience.isEmpty()) {
            throw new RuntimeException("The tools of MCP tool server '" + label + "' are unavailable: it has no "
                                       + "token audience set, which Stroom needs to sign you in to it. Ask an "
                                       + "administrator. Built-in tools still work.");
        }
        if (!server.isTrusted()) {
            throw new RuntimeException("The tools of MCP tool server '" + label + "' are unavailable: Stroom only "
                                       + "signs you in to a trusted server, as it can then act as you. Ask an "
                                       + "administrator. Built-in tools still work.");
        }
        if (!isSecureEndpoint(server.getEndpoint())) {
            throw new RuntimeException("The tools of MCP tool server '" + label + "' are unavailable: Stroom only "
                                       + "signs you in to a server over https (or on the same machine). Ask an "
                                       + "administrator. Built-in tools still work.");
        }
        if (userIdentity.get() == null) {
            throw new RuntimeException("MCP tool server '" + label
                                       + "': there is no authenticated Stroom user to sign in as");
        }
        // Check a token can be had now, then get a current one for every request.
        issuedTokenHeaders(userIdentity.get(), audience, label, callTimeout);
        return () -> issuedTokenHeaders(userIdentity.get(), audience, label, callTimeout);
    }

    private Map<String, String> issuedTokenHeaders(final UserIdentity userIdentity,
                                                   final String audience,
                                                   final String label,
                                                   final Duration callTimeout) {
        // The token the user signed in with, so the issued one is revoked with it when they sign out.
        final String signInJwt = userIdentity instanceof final HasJwt hasJwt
                ? hasJwt.getJwt()
                : null;
        final String token = userTokenIssuerProvider.get()
                // Long enough for the request it is sent with, however long that may take.
                .getAccessToken(userIdentity.subjectId(), audience, signInJwt, callTimeout)
                .orElseThrow(() -> new RuntimeException("MCP tool server '" + label
                                                        + "': Stroom could not issue a token to sign you in"));
        return Map.of("Authorization", "Bearer " + token);
    }

    /// @return True if the endpoint is https, or http on the same machine, where the token doesn't cross a
    /// network.
    static boolean isSecureEndpoint(final String endpoint) {
        try {
            final URI uri = URI.create(NullSafe.trim(endpoint));
            final String scheme = NullSafe.string(uri.getScheme()).toLowerCase(Locale.ROOT);
            final String host = NullSafe.string(uri.getHost()).toLowerCase(Locale.ROOT);
            return "https".equals(scheme)
                   || ("http".equals(scheme)
                       && ("localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host)));
        } catch (final IllegalArgumentException e) {
            return false;
        }
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
     * @return an {@link SSLContext} when the TLS config needs one (custom trust/key store, trust-self-signed or
     * not verifying host names), otherwise null so the JDK default context is used.
     */
    private SSLContext buildSslContext(final HttpClientConfig httpClientConfig, final String label) {
        final HttpTlsConfiguration tls = httpConfigResolver.resolve(httpClientConfig.getTls());
        if (tls == null) {
            return null;
        }

        final boolean hasKeyStore = NullSafe.isNonBlankString(tls.getKeyStorePath());
        final boolean hasTrustStore = NullSafe.isNonBlankString(tls.getTrustStorePath());
        final boolean trustAll = tls.isTrustSelfSignedCertificates();
        final boolean verifyHostname = tls.isVerifyHostname();
        if (!hasKeyStore && !hasTrustStore && !trustAll && verifyHostname) {
            return null;
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
            sslContext.init(keyManagers, verifyHostname
                    ? trustManagers
                    : hostnameIgnoring(trustManagers, label), new SecureRandom());
            return sslContext;
        } catch (final NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new RuntimeException("MCP tool server '" + label + "': failed to build TLS context - "
                                       + e.getMessage(), e);
        }
    }

    // The trust managers, or the JDK's default ones if there are none, made not to check host names.
    private static TrustManager[] hostnameIgnoring(final TrustManager[] trustManagers, final String label)
            throws NoSuchAlgorithmException, KeyStoreException {
        LOGGER.debug(() -> "MCP tool server '" + label + "': not verifying host names");
        if (trustManagers != null) {
            return HostnameIgnoringTrustManager.wrap(trustManagers);
        }
        final TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        return HostnameIgnoringTrustManager.wrap(factory.getTrustManagers());
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

    /// @return The timeout, or the default if none is set or it is zero (as saved by an HTTP client config dialog
    /// opened without defaults), which the HTTP client would refuse.
    static Duration toDuration(final SimpleDuration simpleDuration) {
        final Duration duration = simpleDuration != null
                ? stroom.util.time.SimpleDurationUtil.convertToStroomDuration(simpleDuration).getDuration()
                : null;
        return duration != null && duration.isPositive()
                ? duration
                : stroom.util.time.SimpleDurationUtil.convertToStroomDuration(DEFAULT_TIMEOUT).getDuration();
    }
}
