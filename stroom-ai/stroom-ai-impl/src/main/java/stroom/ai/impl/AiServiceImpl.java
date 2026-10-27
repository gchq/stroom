/*
 * Copyright 2025 Crown Copyright
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
import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
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
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.net.SsrfGuard;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.cohere.CohereScoringModel;
import dev.langchain4j.model.cohere.CohereScoringModel.CohereScoringModelBuilder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.jina.JinaScoringModel;
import dev.langchain4j.model.jina.JinaScoringModel.JinaScoringModelBuilder;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel.OpenAiChatModelBuilder;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel.OpenAiEmbeddingModelBuilder;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Singleton
public class AiServiceImpl implements AiService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiServiceImpl.class);

    /**
     * How much of a failed response body to include in the error we report back to the user. Enough to
     * see what the endpoint objected to, not so much that an HTML error page fills the screen.
     */
    private static final int MAX_ERROR_BODY_LENGTH = 2_000;

    /**
     * Sentinel model ID that activates the stub {@link ChatModel} for offline testing.
     * Create an OpenAIModel document with this as the modelId — no API key or base URL needed.
     */
    public static final String STUB_MODEL_ID = "__stub__";

    private static final String CHAT_RESPONSE_CACHE_NAME = "AI Chat Response Cache";

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
    private final StroomCache<ChatKey, String> chatResponseCache;

    private HttpClientConfig defaultHttpClientConfig;

    @Inject
    AiServiceImpl(final Provider<OpenAIModelStore> openAIModelStoreProvider,
                  final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                  final Provider<StoredSecrets> storedSecretsProvider,
                  final HttpConfigResolver httpConfigResolver,
                  final Provider<HttpClientProviderCache> httpClientCacheProvider,
                  final SecurityContext securityContext,
                  final AiDao aiDao,
                  final CacheManager cacheManager,
                  final Provider<AiConfig> aiConfigProvider) {
        this.openAIModelStoreProvider = openAIModelStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.storedSecretsProvider = storedSecretsProvider;
        this.httpConfigResolver = httpConfigResolver;
        this.httpClientCacheProvider = httpClientCacheProvider;
        this.securityContext = securityContext;
        this.aiDao = aiDao;
        this.chatResponseCache = cacheManager.create(
                CHAT_RESPONSE_CACHE_NAME,
                () -> aiConfigProvider.get().getChatResponseCache());
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
        final String modelId = modelDoc.getModelId();
        final String url = getUrl(modelDoc, "models");

        try (final HttpClientProvider httpClientProvider = httpClientProviderCache.get(httpClientConfiguration)) {
            final HttpResult listed = get(httpClientProvider, url, modelDoc, httpClientConfig);
            if (!listed.isSuccess()) {
                throw new RuntimeException(describeFailure(url, listed, httpClientConfig));
            }

            // Reaching the endpoint is not enough: the model must be one it serves, or every chat fails. What
            // the endpoint says of that model is the answer, not the whole list.
            final Optional<JsonNode> entry = modelEntry(listed.body(), modelId);
            if (entry.isPresent()) {
                return JsonUtil.getMapper(true).writeValueAsString(entry.get());
            }

            // Not listed. Some endpoints (gateways routing models by pattern, say) serve models they don't
            // list, so ask for it by its ID before saying it isn't there.
            final String modelUrl = url + "/" + URLEncoder.encode(modelId, StandardCharsets.UTF_8)
                    .replace("+", "%20");
            final HttpResult retrieved = get(httpClientProvider, modelUrl, modelDoc, httpClientConfig);
            if (retrieved.isSuccess()) {
                return modelDescription(retrieved.body(), modelId);
            }
            throw new ModelNotFoundException(modelId, url, modelIds(listed.body()));
        } catch (final IOException e) {
            LOGGER.debug(() -> "getModel: GET '" + url + "' failed: " + e.getMessage(), e);
            throw new UncheckedIOException("Error requesting '" + url + "': " + e.getMessage(), e);
        }
    }

    /// The entry an OpenAI-compatible `/models` response lists for a model: its `id` and whatever else the
    /// endpoint says of it (`owned_by`, `created`, its context length, ...).
    ///
    /// @param body    The response body.
    /// @param modelId The model's ID.
    /// @return Its entry; empty when the body lists no such model, or isn't such a list.
    static Optional<JsonNode> modelEntry(final String body, final String modelId) {
        for (final JsonNode model : listedModels(body)) {
            if (model.path("id").isTextual() && model.path("id").asText().equals(modelId)) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }

    /// What a `/models/{id}` response says of the model, as indented JSON.
    ///
    /// @param body    The response body.
    /// @param modelId The model's ID.
    /// @return The model's JSON; a short statement it was found when the body isn't JSON.
    static String modelDescription(final String body, final String modelId) {
        try {
            final JsonNode model = JsonUtil.getMapper().readTree(NullSafe.string(body));
            if (model.isObject()) {
                return JsonUtil.getMapper(true).writeValueAsString(model);
            }
        } catch (final JacksonException e) {
            LOGGER.debug(() -> "modelDescription: not JSON: " + e.getMessage(), e);
        }
        return "Model '" + modelId + "' was found.";
    }

    /// The IDs of the models an OpenAI-compatible `/models` response lists (`data[].id`).
    ///
    /// @param body The response body.
    /// @return The IDs, in the order listed; none when the body isn't such a list.
    static List<String> modelIds(final String body) {
        final List<String> ids = new ArrayList<>();
        for (final JsonNode model : listedModels(body)) {
            final JsonNode id = model.path("id");
            if (id.isTextual()) {
                ids.add(id.asText());
            }
        }
        return ids;
    }

    // The entries of a /models response's data array; none when the body isn't such a list.
    private static List<JsonNode> listedModels(final String body) {
        if (NullSafe.isBlankString(body)) {
            return List.of();
        }
        try {
            final List<JsonNode> models = new ArrayList<>();
            JsonUtil.getMapper().readTree(body).path("data").forEach(models::add);
            return models;
        } catch (final JacksonException e) {
            LOGGER.debug(() -> "listedModels: not a model list: " + e.getMessage(), e);
            return List.of();
        }
    }

    // A GET of the endpoint, with the model's API key and headers.
    private HttpResult get(final HttpClientProvider httpClientProvider,
                           final String url,
                           final OpenAIModelDoc modelDoc,
                           final HttpClientConfig httpClientConfig) throws IOException {
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
        HttpClientUtil.headers(httpClientConfig).forEach((name, value) -> {
            if (!httpGet.containsHeader(name)) {
                httpGet.addHeader(name, value);
            }
        });

        final HttpResult result = httpClientProvider.get().execute(httpGet, this::readResponse);
        LOGGER.debug(() -> "getModel: GET '" + url + "' returned " + result.code() + " " + result.reasonPhrase()
                           + ", headers: " + result.headers() + ", body:\n" + result.body());
        return result;
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
    public Optional<DocRef> findModelByNameOrUuid(final String nameOrUuid) {
        if (!NullSafe.isNonBlankString(nameOrUuid)) {
            return Optional.empty();
        }

        // The store resolves names and UUIDs by testing VIEW, but USE is the permission you would grant to
        // let a pipeline or query use a model without letting the user see the document itself. Elevate so
        // that USE counts as VIEW here, matching the read that chat() goes on to do.
        return securityContext.useAsReadResult(() -> {
            final OpenAIModelStore store = openAIModelStoreProvider.get();

            // Try by UUID first so that a model named after another model's UUID cannot shadow it.
            final Optional<DocRef> byUuid = store.findByUuid(nameOrUuid);
            if (byUuid.isPresent()) {
                return byUuid;
            }

            LOGGER.debug(() -> "Unable to find OpenAI model by UUID '" + nameOrUuid + "', trying by name");
            final List<DocRef> byName = store.findByName(nameOrUuid);
            if (NullSafe.isEmptyCollection(byName)) {
                return Optional.empty();
            }
            if (byName.size() > 1) {
                // The store orders by UUID, which is random, so this says which one we used rather than
                // implying a rule about which one it will be.
                LOGGER.info(() -> "Multiple OpenAI models found with name '" + nameOrUuid
                                  + "' - using " + byName.getFirst());
            }
            return Optional.of(byName.getFirst());
        });
    }

    @Override
    public String chat(final DocRef modelRef, final String systemPrompt, final String message) {
        Objects.requireNonNull(modelRef, "No model supplied");

        // Read the model ahead of consulting the cache so that the caller's permission to use it is checked
        // on every call, not just on a cache miss. The read is cheap as the doc store caches documents.
        final OpenAIModelDoc modelDoc = getOpenAIModelDoc(modelRef);
        if (modelDoc == null) {
            throw new RuntimeException("Unable to read OpenAI model " + modelRef);
        }

        // Key on the UUID rather than the whole doc so that a rename does not miss the cache. A change to
        // the model's settings will not invalidate cached answers, which is why the cache is time bounded,
        // see AiConfig.getChatResponseCache().
        final ChatKey chatKey = new ChatKey(modelDoc.getUuid(), systemPrompt, message);

        // Deliberately a lookup, then the call, then a put, rather than a loading get. A loading get runs
        // the load inside the cache's mapping function, which holds a lock on the map bin the key falls in
        // for as long as the load takes. A model call can take minutes - the default request timeout is ten
        // - so an unrelated question whose key happened to fall in the same bin would wait on it. The cost
        // is that two identical questions asked at the same moment both reach the model; asked one after
        // the other, which is what a pipeline or a query actually does, the second is still served from
        // the cache.
        final Optional<String> cached = chatResponseCache.getIfPresent(chatKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        // A failure is not cached, so the next caller asks the model again rather than being told for the
        // next ten minutes what went wrong once.
        final String answer = doChat(modelDoc, systemPrompt, message);
        if (answer != null) {
            chatResponseCache.put(chatKey, answer);
        }
        return answer;
    }

    private String doChat(final OpenAIModelDoc modelDoc, final String systemPrompt, final String message) {
        final List<ChatMessage> messages = new ArrayList<>(2);
        if (NullSafe.isNonBlankString(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(Objects.requireNonNullElse(message, "")));

        final ChatModel chatModel = getChatModel(modelDoc);
        final ChatResponse chatResponse = LOGGER.logDurationIfDebugEnabled(
                () -> chatModel.chat(messages),
                r -> "chat: asked model '" + modelDoc.getModelId() + "'");

        return NullSafe.get(chatResponse, ChatResponse::aiMessage, AiMessage::text);
    }

    @Override
    public ChatModel getChatModel(final OpenAIModelDoc modelDoc) {
        // Stub mode: return a test ChatModel that requires no API key or network.
        if (STUB_MODEL_ID.equals(modelDoc.getModelId())) {
            LOGGER.info(() -> "Using stub ChatModel for testing (modelId='" + STUB_MODEL_ID + "')");
            return new StubChatModel();
        }

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
        // Only ask for the model's reasoning if it is going to be shown.
        modelBuilder.returnThinking(modelDoc.isShowReasoning());

        return LOGGER.logDurationIfDebugEnabled(
                modelBuilder::build,
                r -> "getChatModel: built model '" + modelDoc.getModelId() + "'");
    }

    @Override
    public StreamingChatModel getStreamingChatModel(final OpenAIModelDoc modelDoc) {
        if (STUB_MODEL_ID.equals(modelDoc.getModelId())) {
            return new StubStreamingChatModel(new StubChatModel());
        }

        final OpenAiStreamingChatModelBuilder modelBuilder = OpenAiStreamingChatModel.builder()
                .modelName(modelDoc.getModelId());
        // Need to specify HTTP 1.1 for vLLM interoperability
        // Ref: https://github.com/langchain4j/langchain4j/issues/3682
        modelBuilder.httpClientBuilder(getClientBuilder(modelDoc));
        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Reject cloud-metadata/wildcard targets to prevent SSRF, as for the non-streaming model.
            SsrfGuard.rejectMetadataAndWildcard(modelDoc.getBaseUrl());
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }
        getApiKey(modelDoc).ifPresent(modelBuilder::apiKey);
        if (NullSafe.isNonEmptyString(modelDoc.getReasoningEffort())) {
            modelBuilder.reasoningEffort(modelDoc.getReasoningEffort());
        }
        modelBuilder.returnThinking(modelDoc.isShowReasoning());
        return modelBuilder.build();
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
        final HttpClientConfig httpClientConfig = NullSafe.getOrElse(
                modelDoc,
                OpenAIModelDoc::getHttpClientConfiguration,
                getDefaultHttpClientConfig());
        return new ApacheHttpClientBuilder(httpClientCacheProvider.get(),
                httpConfigResolver.resolve(httpClientConfig),
                HttpClientUtil.headers(httpClientConfig));
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
    public List<AiChatMessage> getStreamingMessages(final int chatId) {
        verifyOwnership(chatId);
        return aiDao.getStreamingMessages(chatId);
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

    @Override
    public void askAsQuestion(final int messageId, final String question) {
        aiDao.askAsQuestion(messageId, question);
    }

    // No ownership check — internal-only, called from background processing.
    @Override
    public boolean changeMessageType(final int chatId,
                                     final int messageId,
                                     final Set<AiMessageType> from,
                                     final AiMessageType to) {
        return aiDao.changeMessageType(chatId, messageId, from, to);
    }

    // No ownership check — internal-only, called from background processing.
    @Override
    public int changeMessageTypes(final int chatId, final Set<AiMessageType> from, final AiMessageType to) {
        return aiDao.changeMessageTypes(chatId, from, to);
    }

    // No ownership check — internal-only, called from background processing.
    @Override
    public boolean hasMessageOfType(final int chatId, final AiMessageType type) {
        return aiDao.hasMessageOfType(chatId, type);
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

    // --------------------------------------------------------------------------------


    /**
     * The identity of a question, for caching purposes. Two questions are the same question if they ask the
     * same model the same thing with the same system prompt.
     */
    private record ChatKey(String modelUuid, String systemPrompt, String message) {

    }

}
