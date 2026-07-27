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

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.credentials.api.KeyStore;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.SecurityContext;
import stroom.util.http.HttpAuthConfiguration;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpProxyConfiguration;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.jersey.HttpClientProvider;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.SsrfGuard;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.http.HttpAuthConfig;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpProxyConfig;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.SimpleDurationUtil;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.cohere.CohereScoringModel.CohereScoringModelBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.jina.JinaScoringModel;
import dev.langchain4j.model.jina.JinaScoringModel.JinaScoringModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder;
import dev.langchain4j.model.scoring.ScoringModel;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

@Singleton
public class AiServiceImpl implements AiService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiServiceImpl.class);

    private static final SimpleDuration DEFAULT_TIMEOUT = SimpleDuration
            .builder()
            .time(10)
            .timeUnit(TimeUnit.MINUTES)
            .build();

    private final Provider<OpenAIModelStore> openAIModelStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<StoredSecrets> storedSecretsProvider;
    private final Provider<HttpClientProviderCache> httpClientCacheProvider;
    private final SecurityContext securityContext;
    private final AiDao aiDao;

    private HttpClientConfig defaultHttpClientConfig;

    @Inject
    AiServiceImpl(final Provider<OpenAIModelStore> openAIModelStoreProvider,
                  final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                  final Provider<StoredSecrets> storedSecretsProvider,
                  final Provider<HttpClientProviderCache> httpClientCacheProvider,
                  final SecurityContext securityContext,
                  final AiDao aiDao) {
        this.openAIModelStoreProvider = openAIModelStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.storedSecretsProvider = storedSecretsProvider;
        this.httpClientCacheProvider = httpClientCacheProvider;
        this.securityContext = securityContext;
        this.aiDao = aiDao;
    }

    @Override
    public String getModel(final OpenAIModelDoc modelDoc) {
        try {
            // curl https://api.openai.com/v1/models \
            //   -H "Authorization: Bearer $OPENAI_API_KEY"


            final HttpClientConfiguration httpClientConfiguration = convert(NullSafe.getOrElse(
                    modelDoc,
                    OpenAIModelDoc::getHttpClientConfiguration,
                    getDefaultHttpClientConfig()));
            final HttpClientProviderCache httpClientProviderCache = httpClientCacheProvider.get();
            try (final HttpClientProvider httpClientProvider = httpClientProviderCache.get(httpClientConfiguration)) {
                final String url = getUrl(modelDoc, "models");
                // Reject cloud-metadata/wildcard targets to prevent SSRF.
                SsrfGuard.rejectMetadataAndWildcard(url);

                final HttpGet httpGet = new HttpGet(url);
                // Do not follow redirects - a redirect could otherwise reach a blocked address after the
                // check above, since this client's redirect behaviour is request-supplied.
                httpGet.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
                httpGet.addHeader("Content-Type", "application/audit");

                // Provide an API key
                getApiKey(modelDoc).ifPresent(apiKey ->
                        httpGet.addHeader("Authorization", "Bearer " + apiKey));

                return httpClientProvider.get().execute(httpGet, response -> {
//                        final StringBuilder sb = new StringBuilder()
//                    .append("Model ID: ")
//                    .append(model.id())
//                    .append("\nCreated: ")
//                    .append(DateUtil.createNormalDateTimeString(model.created()))
//                    .append("\nOwner: ")
//                    .append(model.ownedBy())
//                    .append("\nValid: ")
//                    .append(model.isValid());

                    if (response.getCode() != 200) {
                        return response.toString();
                    }

                    final byte[] bytes = response.getEntity().getContent().readAllBytes();
                    return new String(bytes, StandardCharsets.UTF_8);
                });
            }

//        final OpenAIOkHttpClient.Builder clientBuilder = OpenAIOkHttpClient.builder()
//                .fromEnv();
//
//        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
//            // Override the base URL
//            clientBuilder.baseUrl(modelDoc.getBaseUrl());
//        }
//
//        final String apiKey = getApiKey(modelDoc);
//        // Provide a bearer token
//        clientBuilder.credential(BearerTokenCredential.create(apiKey));
//
//        final OpenAIClient client = clientBuilder.build();
//        return client.models().list().items().stream()
//                .filter(model -> modelDoc.getModelId().equals(model.id()))
//                .findFirst().orElseThrow();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Optional<String> getApiKey(final OpenAIModelDoc doc) {
        final String apiKeyName = doc.getApiKeyName();
        if (NullSafe.isNonBlankString(apiKeyName)) {
            final StoredSecret storedSecret = storedSecretsProvider.get().get(apiKeyName);
            if (storedSecret != null) {
                if (storedSecret.secret() instanceof final AccessTokenSecret accessTokenSecret) {
                    if (accessTokenSecret.getAccessToken() != null) {
                        return Optional.of(accessTokenSecret.getAccessToken());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private String getUrl(final OpenAIModelDoc modelDoc, final String path) {
        String url = Objects.requireNonNullElse(modelDoc.getBaseUrl(), "https://api.openai.com/v1");
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        if (NullSafe.isNonBlankString(path)) {
            url = url + path;
        }
        return url;
    }

    @Override
    public OpenAIModelDoc getOpenAIModelDoc(final DocRef docRef) {
        return documentResourceHelperProvider.get().read(openAIModelStoreProvider.get(), docRef);
    }

    @Override
    public ChatModel getChatModel(final OpenAIModelDoc modelDoc) {
        LOGGER.debug(() -> "getChatModel: modelId='" + modelDoc.getModelId()
                           + "' baseUrl='" + NullSafe.toString(modelDoc.getBaseUrl()) + "'");

        final OpenAiChatModelBuilder modelBuilder = OpenAiChatModel.builder()
                .modelName(modelDoc.getModelId());

        // Need to specify HTTP 1.1 for vLLM interoperability
        // Ref: https://github.com/langchain4j/langchain4j/issues/3682
        modelBuilder.httpClientBuilder(getClientBuilder(modelDoc));

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL. Reject cloud-metadata/wildcard targets to prevent SSRF (private and
            // loopback are allowed, since a self-hosted OpenAI-compatible model legitimately lives there).
            SsrfGuard.rejectMetadataAndWildcard(modelDoc.getBaseUrl());
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        // Provide an API key
        getApiKey(modelDoc).ifPresent(modelBuilder::apiKey);

        if (NullSafe.isNonEmptyString(modelDoc.getReasoningEffort())) {
            modelBuilder.reasoningEffort(modelDoc.getReasoningEffort());
        }

        return LOGGER.logDurationIfDebugEnabled(
                modelBuilder::build,
                r -> "getChatModel: built model '" + modelDoc.getModelId() + "'");
    }

    @Override
    public EmbeddingModel getEmbeddingModel(final OpenAIModelDoc modelDoc) {
        final OpenAiEmbeddingModelBuilder modelBuilder = OpenAiEmbeddingModel.builder()
                .modelName(modelDoc.getModelId());

        // Need to specify HTTP 1.1 for vLLM interoperability
        // Ref: https://github.com/langchain4j/langchain4j/issues/3682
        modelBuilder.httpClientBuilder(getClientBuilder(modelDoc));

        // Set embedding dimensions
        if (modelDoc.getEmbeddingModelDimensions() > 0) {
            modelBuilder.dimensions(modelDoc.getEmbeddingModelDimensions());
        }

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL. Reject cloud-metadata/wildcard targets to prevent SSRF (private and
            // loopback are allowed, since a self-hosted OpenAI-compatible model legitimately lives there).
            SsrfGuard.rejectMetadataAndWildcard(modelDoc.getBaseUrl());
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        // Provide an API key
        getApiKey(modelDoc).ifPresent(modelBuilder::apiKey);

        return modelBuilder.build();
    }

    private HttpClientBuilder getClientBuilder(final OpenAIModelDoc modelDoc) {
        final HttpClientConfiguration httpClientConfiguration = convert(NullSafe.getOrElse(
                modelDoc,
                OpenAIModelDoc::getHttpClientConfiguration,
                getDefaultHttpClientConfig()));
        return new ApacheHttpClientBuilder(httpClientCacheProvider.get(), httpClientConfiguration);
    }

    @Override
    public ScoringModel getCohereScoringModel(final OpenAIModelDoc modelDoc) {
        final CohereScoringModelBuilder modelBuilder = CohereScoringModel.builder()
                .modelName(modelDoc.getModelId());

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL. Reject cloud-metadata/wildcard targets to prevent SSRF (private and
            // loopback are allowed, since a self-hosted OpenAI-compatible model legitimately lives there).
            SsrfGuard.rejectMetadataAndWildcard(modelDoc.getBaseUrl());
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        // Provide an API key
        getApiKey(modelDoc).ifPresent(modelBuilder::apiKey);

        return modelBuilder.build();
    }

    @Override
    public ScoringModel getJinaScoringModel(final OpenAIModelDoc modelDoc) {
        final JinaScoringModelBuilder modelBuilder = JinaScoringModel.builder()
                .modelName(modelDoc.getModelId());

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL. Reject cloud-metadata/wildcard targets to prevent SSRF (private and
            // loopback are allowed, since a self-hosted OpenAI-compatible model legitimately lives there).
            SsrfGuard.rejectMetadataAndWildcard(modelDoc.getBaseUrl());
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        // Provide an API key
        getApiKey(modelDoc).ifPresent(modelBuilder::apiKey);

        return modelBuilder.build();
    }

    private HttpClientConfiguration convert(final HttpClientConfig config) {
        Objects.requireNonNull(config, "Null HTTP client configuration");

        return HttpClientConfiguration
                .builder()
                .timeout(SimpleDurationUtil.convertToStroomDuration(config.getTimeout()))
                .connectionTimeout(SimpleDurationUtil.convertToStroomDuration(config.getConnectionTimeout()))
                .connectionRequestTimeout(
                        SimpleDurationUtil.convertToStroomDuration(config.getConnectionRequestTimeout()))
                .timeToLive(SimpleDurationUtil.convertToStroomDuration(config.getTimeToLive()))
                .cookiesEnabled(config.isCookiesEnabled())
                .maxConnections(config.getMaxConnections())
                .maxConnectionsPerRoute(config.getMaxConnectionsPerRoute())
                .keepAlive(SimpleDurationUtil.convertToStroomDuration(config.getKeepAlive()))
                .retries(config.getRetries())
                .userAgent(config.getUserAgent())
                .proxyConfiguration(convert(config.getProxy()))
                .validateAfterInactivityPeriod(
                        SimpleDurationUtil.convertToStroomDuration(config.getValidateAfterInactivityPeriod()))
                .tlsConfiguration(convert(config.getTls()))
                .build();
    }

    private HttpProxyConfiguration convert(final HttpProxyConfig config) {
        if (config == null) {
            return null;
        }

        return HttpProxyConfiguration
                .builder()
                .host(config.getHost())
                .port(config.getPort())
                .scheme(config.getScheme())
                .auth(convert(config.getAuth()))
                .nonProxyHosts(config.getNonProxyHosts())
                .build();
    }

    private HttpAuthConfiguration convert(final HttpAuthConfig config) {
        if (config == null) {
            return null;
        }

        return HttpAuthConfiguration
                .builder()
                .username(config.getUsername())
                .password(config.getPassword())
                .authScheme(config.getAuthScheme())
                .realm(config.getRealm())
                .hostname(config.getHostname())
                .domain(config.getDomain())
                .credentialType(config.getCredentialType())
                .build();
    }

    private HttpTlsConfiguration convert(final HttpTlsConfig config) {
        if (config == null) {
            return null;
        }

        final HttpTlsConfiguration.Builder builder = HttpTlsConfiguration.builder();
        if (NullSafe.isNonBlankString(config.getKeyStoreName())) {
            final KeyStore keyStore = storedSecretsProvider.get().getKeyStore(config.getKeyStoreName());
            builder
                    .keyStorePath(keyStore.keyStorePath())
                    .keyStorePassword(keyStore.keyStorePassword())
                    .keyStoreType(keyStore.keyStoreType())
                    .keyStoreProvider(keyStore.keyStoreProvider());
        }

        if (NullSafe.isNonBlankString(config.getTrustStoreName())) {
            final KeyStore trustStore = storedSecretsProvider.get().getKeyStore(config.getTrustStoreName());
            builder
                    .trustStorePath(trustStore.keyStorePath())
                    .trustStorePassword(trustStore.keyStorePassword())
                    .trustStoreType(trustStore.keyStoreType())
                    .trustStoreProvider(trustStore.keyStoreProvider());
        }

        return builder
                .protocol(config.getProtocol())
                .provider(config.getProvider())
                .trustSelfSignedCertificates(config.isTrustSelfSignedCertificates())
                .verifyHostname(config.isVerifyHostname())
                .supportedProtocols(config.getSupportedProtocols())
                .supportedCiphers(config.getSupportedCiphers())
                .certAlias(config.getCertAlias())
                .build();
    }

    // ---------------------------------------------------------------------
    // Chat persistence operations (delegate to AiDao)
    // ---------------------------------------------------------------------

    @Override
    public AiChat createChat() {
        return aiDao.createChat(securityContext.getUserRef());
    }

    @Override
    public ResultPage<AiChat> listChats(final FindAiChatHistoryCriteria criteria) {
        return aiDao.listChats(securityContext.getUserRef(), criteria);
    }

    @Override
    public AiChat getChat(final int chatId) {
        final AiChat chat = aiDao.getChat(chatId)
                .orElseThrow(() -> new RuntimeException("Chat not found: " + chatId));
        verifyOwnership(chat);
        return chat;
    }

    @Override
    public void verifyOwnership(final int chatId) {
        verifyOwnership(getChat(chatId));
    }

    @Override
    public void verifyOwnership(final AiChat chat) {
        final String currentUserUuid = securityContext.getUserRef().getUuid();
        LOGGER.trace(() -> "verifyOwnership: chatId=" + chat.getId()
                           + " owner=" + chat.getUserUuid()
                           + " currentUser=" + currentUserUuid);
        if (!currentUserUuid.equals(chat.getUserUuid())) {
            throw new RuntimeException("Access denied: chat " + chat.getId()
                                       + " does not belong to the current user");
        }
    }

    @Override
    public void updateChatTitle(final int chatId, final String title) {
        verifyOwnership(chatId);
        aiDao.updateChatTitle(chatId, title);
    }

    @Override
    public void deleteChat(final int chatId) {
        verifyOwnership(chatId);
        aiDao.deleteChat(chatId);
    }

    @Override
    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final String message) {
        verifyOwnership(chatId);
        return aiDao.storeMessage(chatId, messageType, message);
    }

    @Override
    public List<AiChatMessage> getMessages(final int chatId) {
        return aiDao.getMessages(chatId);
    }

    @Override
    public List<AiChatMessage> getMessagesSince(final int chatId, final int lastSeenMessageId) {
        verifyOwnership(chatId);
        return aiDao.getMessagesSince(chatId, lastSeenMessageId);
    }

    @Override
    public AiChatMessage storeMessage(final int chatId,
                                      final AiMessageType messageType,
                                      final Integer attachmentId,
                                      final String message) {
        verifyOwnership(chatId);
        return aiDao.storeMessage(chatId, messageType, attachmentId, message);
    }

    // No ownership check — internal-only, called from background processing.
    @Override
    public void updateMessageText(final int messageId, final String message) {
        aiDao.updateMessageText(messageId, message);
    }

    // No ownership check — internal-only, called from background processing.
    @Override
    public void deleteMessage(final int messageId) {
        aiDao.deleteMessage(messageId);
    }

    @Override
    public void deleteAttachment(final int attachmentId) {
        aiDao.deleteAttachment(attachmentId);
    }

    @Override
    public void deleteAllChatMessagesAndAttachments(final int chatId) {
        verifyOwnership(chatId);
        aiDao.deleteAllChatMessagesAndAttachments(chatId);
    }

    // ---------------------------------------------------------------------
    // Attachment operations (delegate to AiDao)
    // ---------------------------------------------------------------------

    @Override
    public AiChatAttachment createAttachment(final int chatId,
                                             final AiAttachmentType type,
                                             final String contextJson) {
        verifyOwnership(chatId);
        final AiChatAttachment attachment = aiDao.createAttachment(chatId, type, contextJson);
        LOGGER.debug(() -> "createAttachment: chatId=" + chatId
                           + " type=" + type
                           + " attachmentId=" + attachment.getId());
        return attachment;
    }

    // No ownership check — internal-only, called from async download threads.
    @Override
    public void updateAttachmentStatus(final int attachmentId,
                                       final AiAttachmentStatus status,
                                       final Integer rowCount,
                                       final String description,
                                       final String errorMessage,
                                       final boolean truncated) {
        LOGGER.debug(() -> "updateAttachmentStatus: attachmentId=" + attachmentId
                           + " status=" + status
                           + (rowCount != null
                ? " rows=" + rowCount
                : "")
                           + (errorMessage != null
                ? " error='" + errorMessage + "'"
                : ""));
        aiDao.updateAttachmentStatus(attachmentId, status, rowCount, description, errorMessage, truncated);
    }

    // No ownership check — internal-only, resolves by attachment ID not chat.
    @Override
    public Optional<AiChatAttachment> getAttachment(final int attachmentId) {
        return aiDao.getAttachment(attachmentId);
    }

    @Override
    public List<AiChatAttachment> getAttachmentsByChatId(final int chatId) {
        verifyOwnership(chatId);
        return aiDao.getAttachmentsByChatId(chatId);
    }

    @Override
    public HttpClientConfig getDefaultHttpClientConfig() {
        if (defaultHttpClientConfig == null) {
            defaultHttpClientConfig = createDefaultHttpClientConfig();
        }
        return defaultHttpClientConfig;
    }

    private HttpClientConfig createDefaultHttpClientConfig() {
        HttpTlsConfig httpTlsConfig = null;
        try (final SSLServerSocket sslServerSocket = ((SSLServerSocket) SSLServerSocketFactory.getDefault()
                .createServerSocket())) {
            final List<String> supportedCiphers = Arrays.stream(sslServerSocket.getEnabledCipherSuites()).toList();
            final List<String> supportedProtocols = Arrays.stream(sslServerSocket.getEnabledProtocols()).toList();
            httpTlsConfig = HttpTlsConfig
                    .builder()
                    .supportedCiphers(supportedCiphers)
                    .supportedProtocols(supportedProtocols)
                    .build();
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }

        return HttpClientConfig
                .builder()
                .timeout(DEFAULT_TIMEOUT)
                .connectionTimeout(DEFAULT_TIMEOUT)
                .connectionRequestTimeout(DEFAULT_TIMEOUT)
                .tlsConfiguration(httpTlsConfig)
                .build();
    }
}
