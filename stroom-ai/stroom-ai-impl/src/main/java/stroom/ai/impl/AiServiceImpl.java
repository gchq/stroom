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
import stroom.credentials.api.HttpConfigResolver;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.api.SecurityContext;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientUtil;
import stroom.util.jersey.HttpClientProvider;
import stroom.util.jersey.HttpClientProviderCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.SsrfGuard;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

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
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class AiServiceImpl implements AiService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiServiceImpl.class);

    /**
     * How much of a failed response body to include in the error we report back to the user. Enough to
     * see what the endpoint objected to, not so much that an HTML error page fills the screen.
     */
    private static final int MAX_ERROR_BODY_LENGTH = 2_000;

    private static final SimpleDuration DEFAULT_TIMEOUT = SimpleDuration
            .builder()
            .time(10)
            .timeUnit(TimeUnit.MINUTES)
            .build();

    private final Provider<OpenAIModelStore> openAIModelStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<StoredSecrets> storedSecretsProvider;
    private final HttpConfigResolver httpConfigResolver;
    private final Provider<HttpClientProviderCache> httpClientCacheProvider;
    private final SecurityContext securityContext;
    private final AiDao aiDao;

    private HttpClientConfig defaultHttpClientConfig;

    @Inject
    AiServiceImpl(final Provider<OpenAIModelStore> openAIModelStoreProvider,
                  final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                  final Provider<StoredSecrets> storedSecretsProvider,
                  final HttpConfigResolver httpConfigResolver,
                  final Provider<HttpClientProviderCache> httpClientCacheProvider,
                  final SecurityContext securityContext,
                  final AiDao aiDao) {
        this.openAIModelStoreProvider = openAIModelStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.storedSecretsProvider = storedSecretsProvider;
        this.httpConfigResolver = httpConfigResolver;
        this.httpClientCacheProvider = httpClientCacheProvider;
        this.securityContext = securityContext;
        this.aiDao = aiDao;
    }

    @Override
    public String getModel(final OpenAIModelDoc modelDoc) {
        // curl https://api.openai.com/v1/models \
        //   -H "Authorization: Bearer $OPENAI_API_KEY"

        final HttpClientConfig httpClientConfig = NullSafe.getOrElse(
                modelDoc,
                OpenAIModelDoc::getHttpClientConfiguration,
                getDefaultHttpClientConfig());
        final HttpClientConfiguration httpClientConfiguration = httpConfigResolver.resolve(httpClientConfig);
        final HttpClientProviderCache httpClientProviderCache = httpClientCacheProvider.get();
        final String url = getUrl(modelDoc, "models");

        try (final HttpClientProvider httpClientProvider = httpClientProviderCache.get(httpClientConfiguration)) {
            // Reject cloud-metadata/wildcard targets to prevent SSRF. Any redirect the client follows is
            // checked the same way, see ConfiguredRedirectStrategy.
            SsrfGuard.rejectMetadataAndWildcard(url);

            final HttpGet httpGet = new HttpGet(url);
            // A GET has no body, so it is `Accept` rather than `Content-Type` that tells the endpoint what
            // we want back.
            httpGet.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());

            // Provide an API key
            getApiKey(modelDoc).ifPresent(apiKey ->
                    httpGet.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey));

            final HttpResult result = httpClientProvider.get().execute(httpGet, this::readResponse);
            LOGGER.debug(() -> "getModel: GET '" + url + "' returned " + result.code() + " " + result.reasonPhrase()
                               + ", headers: " + result.headers() + ", body:\n" + result.body());

            if (!result.isSuccess()) {
                throw new RuntimeException(describeFailure(url, result, httpClientConfig));
            }

            return result.body();

        } catch (final IOException e) {
            LOGGER.debug(() -> "getModel: GET '" + url + "' failed: " + e.getMessage(), e);
            throw new UncheckedIOException("Error requesting '" + url + "': " + e.getMessage(), e);
        }
    }

    private HttpResult readResponse(final ClassicHttpResponse response) throws IOException {
        final Map<String, String> headers = new LinkedHashMap<>();
        for (final Header header : response.getHeaders()) {
            // First value wins, as that is what getFirstHeader() would have given us.
            headers.putIfAbsent(header.getName().toLowerCase(Locale.ROOT), header.getValue());
        }

        String body = null;
        final HttpEntity entity = response.getEntity();
        if (entity != null) {
            try (final InputStream inputStream = entity.getContent()) {
                body = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        return new HttpResult(response.getCode(), response.getReasonPhrase(), headers, body);
    }

    /**
     * Says what went wrong in terms the person editing the model document can act on, i.e. the status the
     * endpoint gave us and whatever it said about it, rather than just the fact that it was not a 200.
     */
    private String describeFailure(final String url,
                                   final HttpResult result,
                                   final HttpClientConfig httpClientConfig) {
        final StringBuilder sb = new StringBuilder()
                .append("GET ")
                .append(url)
                .append(" returned ")
                .append(result.code());
        if (NullSafe.isNonBlankString(result.reasonPhrase())) {
            sb.append(" ").append(result.reasonPhrase());
        }
        sb.append(".");

        explain(result, httpClientConfig).ifPresent(explanation -> sb.append(" ").append(explanation));

        if (NullSafe.isNonBlankString(result.body())) {
            final String body = result.body().strip();
            sb.append("\nResponse:\n");
            if (body.length() > MAX_ERROR_BODY_LENGTH) {
                sb.append(body, 0, MAX_ERROR_BODY_LENGTH).append("...");
            } else {
                sb.append(body);
            }
        }

        return sb.toString();
    }

    /**
     * Anything we can usefully add to the bare status, for the few cases where the endpoint or our own
     * configuration has told us something more specific than the status code does. Every other failure is
     * left to speak for itself through the status and the response body.
     */
    private Optional<String> explain(final HttpResult result, final HttpClientConfig httpClientConfig) {
        if (result.isRedirect()) {
            final String location = result.header(HttpHeaders.LOCATION)
                    .map(value -> "'" + value + "'")
                    .orElse("an unspecified location");
            return Optional.of(httpClientConfig.isFollowRedirects()
                    ? "The endpoint redirected to " + location + " but the redirect was not followed."
                    : "The endpoint redirected to " + location + ", which was not followed because "
                      + "'Follow Redirects' is turned off in this model's HTTP client configuration.");
        }

        if (result.code() == HttpStatus.SC_UNAUTHORIZED || result.code() == HttpStatus.SC_FORBIDDEN) {
            return Optional.of(result.header(HttpHeaders.WWW_AUTHENTICATE)
                    .map(challenge -> "The endpoint asked for authentication: " + challenge)
                    .orElse("Check the API key set on this model."));
        }

        if (result.code() == HttpStatus.SC_NOT_FOUND) {
            return Optional.of("Check the base URL set on this model.");
        }

        return Optional.empty();
    }

    /**
     * What a response told us, whatever it was, kept for as long as it takes to decide whether it was what
     * we wanted and to say what it was if it was not. The entity is read here because it is only readable
     * while the response is open.
     */
    private record HttpResult(int code,
                              String reasonPhrase,
                              Map<String, String> headers,
                              String body) {

        private boolean isSuccess() {
            return code >= HttpStatus.SC_SUCCESS && code < HttpStatus.SC_REDIRECTION;
        }

        private boolean isRedirect() {
            return code >= HttpStatus.SC_REDIRECTION && code < HttpStatus.SC_CLIENT_ERROR;
        }

        private Optional<String> header(final String name) {
            return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
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
        final HttpClientConfiguration httpClientConfiguration = httpConfigResolver.resolve(NullSafe.getOrElse(
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
    public Optional<AiChatMessage> getWorkingMessage(final int chatId) {
        verifyOwnership(chatId);
        return aiDao.getWorkingMessage(chatId);
    }

    // No ownership check — internal-only, called either side of processing a question.
    @Override
    public void deleteWorkingMessages(final int chatId) {
        aiDao.deleteWorkingMessages(chatId);
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
        return HttpClientUtil.createDefaultHttpClientConfig(DEFAULT_TIMEOUT);
    }
}
