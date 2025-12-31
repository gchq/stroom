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

package stroom.langchain.impl;

import stroom.credentials.api.KeyStore;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.langchain.api.OpenAIModelStore;
import stroom.langchain.api.OpenAIService;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.util.http.HttpAuthConfiguration;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpProxyConfiguration;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.jersey.HttpClientCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpAuthConfig;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpProxyConfig;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.time.StroomDuration;

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
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Singleton
public class OpenAIServiceImpl implements OpenAIService {

    private final Provider<OpenAIModelStore> openAIModelStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<StoredSecrets> storedSecretsProvider;
    private final Provider<HttpClientCache> httpClientCacheProvider;

    @Inject
    OpenAIServiceImpl(final Provider<OpenAIModelStore> openAIModelStoreProvider,
                      final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                      final Provider<StoredSecrets> storedSecretsProvider,
                      final Provider<HttpClientCache> httpClientCacheProvider) {
        this.openAIModelStoreProvider = openAIModelStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.storedSecretsProvider = storedSecretsProvider;
        this.httpClientCacheProvider = httpClientCacheProvider;
    }

    @Override
    public String getModel(final OpenAIModelDoc modelDoc) {
        try {
            // curl https://api.openai.com/v1/models \
            //   -H "Authorization: Bearer $OPENAI_API_KEY"


            final HttpClientCache httpClientCache = httpClientCacheProvider.get();
            final HttpClientConfiguration httpClientConfiguration = new HttpClientConfiguration();
            final HttpClient httpClient = httpClientCache.get(httpClientConfiguration);


            final String url = getUrl(modelDoc, "models");
            final String apiKey = getApiKey(modelDoc);

            final HttpGet httpGet = new HttpGet(url);
            httpGet.addHeader("Content-Type", "application/audit");
            if (NullSafe.isNonBlankString(apiKey)) {
                httpGet.addHeader("Authorization", "Bearer " + apiKey);
            }

            return httpClient.execute(httpGet, response -> {
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

    private String getApiKey(final OpenAIModelDoc doc) {
        return getApiKey(doc.getApiKeyName());
    }

    private String getApiKey(final String apiKeyName) {
        if (NullSafe.isNonBlankString(apiKeyName)) {
            final StoredSecret storedSecret = storedSecretsProvider.get().get(apiKeyName);
            if (storedSecret != null) {
                if (storedSecret.secret() instanceof final AccessTokenSecret accessTokenSecret) {
                    if (accessTokenSecret.getAccessToken() != null) {
                        return accessTokenSecret.getAccessToken();
                    }
                }
            }
        }
        return "";
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
        final String apiKey = getApiKey(modelDoc.getApiKeyName());

        // Need to specify HTTP 1.1 for vLLM interoperability
        // Ref: https://github.com/langchain4j/langchain4j/issues/3682
//        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
//                .version(HttpClient.Version.HTTP_1_1);
//
////                .sslContext()authenticator();

//        final HttpClientConfiguration httpClientConfiguration = new HttpClientConfiguration();
//        final CloseableHttpClient httpClient =
//        httpClientFactoryProvider.get().get(modelName, httpClientConfiguration);

//        final ApacheHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
//                .httpClientBuilder(httpClientBuilder)
//
//              ;

        final HttpClientBuilder httpClientBuilder = getClientBuilder(modelDoc);
        final OpenAiChatModelBuilder modelBuilder = OpenAiChatModel.builder()
                .modelName(modelDoc.getModelId())
                .apiKey(apiKey)
                .httpClientBuilder(httpClientBuilder);

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        return modelBuilder.build();
    }

    @Override
    public EmbeddingModel getEmbeddingModel(final OpenAIModelDoc modelDoc) {
        final String apiKey = getApiKey(modelDoc.getApiKeyName());

        // Need to specify HTTP 1.1 for vLLM interoperability
        // Ref: https://github.com/langchain4j/langchain4j/issues/3682
//        final HttpClient.Builder httpClientBuilder = HttpClient.newBuilder()
//                .version(HttpClient.Version.HTTP_1_1);
//        final JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
//                .httpClientBuilder(httpClientBuilder);

        final HttpClientBuilder httpClientBuilder = getClientBuilder(modelDoc);
        final OpenAiEmbeddingModelBuilder modelBuilder = OpenAiEmbeddingModel.builder()
                .modelName(modelDoc.getModelId())
                .apiKey(apiKey)
                .httpClientBuilder(httpClientBuilder);

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        // Provide a bearer token
        modelBuilder.apiKey(getApiKey(modelDoc));

        return modelBuilder.build();
    }

    private HttpClientBuilder getClientBuilder(final OpenAIModelDoc modelDoc) {
        final HttpClientConfiguration httpClientConfiguration = convert(NullSafe.getOrElse(
                modelDoc,
                OpenAIModelDoc::getHttpClientConfiguration,
                HttpClientConfig.builder().build()));
        final HttpClientCache httpClientCache = httpClientCacheProvider.get();
        final HttpClient httpClient = httpClientCache.get(httpClientConfiguration);
        return new ApacheHttpClientBuilder(httpClient, httpClientConfiguration);
    }

    @Override
    public ScoringModel getCohereScoringModel(final OpenAIModelDoc modelDoc) {
        final CohereScoringModelBuilder modelBuilder = CohereScoringModel.builder()
                .modelName(modelDoc.getModelId());

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        modelBuilder.apiKey(getApiKey(modelDoc));
//        } else {
//            modelBuilder.apiKey("dummy_api_key");
//        }

        return modelBuilder.build();
    }

    @Override
    public ScoringModel getJinaScoringModel(final OpenAIModelDoc modelDoc) {
        final JinaScoringModelBuilder modelBuilder = JinaScoringModel.builder()
                .modelName(modelDoc.getModelId());

        if (NullSafe.isNonEmptyString(modelDoc.getBaseUrl())) {
            // Override the base URL
            modelBuilder.baseUrl(modelDoc.getBaseUrl());
        }

        // Provide a bearer token
        modelBuilder.apiKey(getApiKey(modelDoc));
//        } else {
//            modelBuilder.apiKey("dummy_api_key");
//        }

        return modelBuilder.build();
    }

    private HttpClientConfiguration convert(final HttpClientConfig config) {
        if (config == null) {
            return null;
        }

        return HttpClientConfiguration
                .builder()
                .timeout(convert(config.getTimeout()))
                .connectionTimeout(convert(config.getConnectionTimeout()))
                .connectionRequestTimeout(convert(config.getConnectionRequestTimeout()))
                .timeToLive(convert(config.getTimeToLive()))
                .cookiesEnabled(config.isCookiesEnabled())
                .maxConnections(config.getMaxConnections())
                .maxConnectionsPerRoute(config.getMaxConnectionsPerRoute())
                .keepAlive(convert(config.getKeepAlive()))
                .retries(config.getRetries())
                .userAgent(config.getUserAgent())
                .proxyConfiguration(convert(config.getProxy()))
                .validateAfterInactivityPeriod(convert(config.getValidateAfterInactivityPeriod()))
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
                .provider(config.getProvider())
                .trustSelfSignedCertificates(config.isTrustSelfSignedCertificates())
                .verifyHostname(config.isVerifyHostname())
                .supportedProtocols(config.getSupportedProtocols())
                .supportedCiphers(config.getSupportedCiphers())
                .certAlias(config.getCertAlias())
                .build();
    }

    private StroomDuration convert(final SimpleDuration duration) {
        if (duration == null || duration.getTimeUnit() == null) {
            return null;
        }

        return switch (duration.getTimeUnit()) {
            case NANOSECONDS -> StroomDuration.ofNanos(duration.getTime());
            case MILLISECONDS -> StroomDuration.ofMillis(duration.getTime());
            case SECONDS -> StroomDuration.ofSeconds(duration.getTime());
            case MINUTES -> StroomDuration.ofMinutes(duration.getTime());
            case HOURS -> StroomDuration.ofHours(duration.getTime());
            case DAYS -> StroomDuration.ofDays(duration.getTime());
            case WEEKS -> StroomDuration.ofDays(duration.getTime() * 7);
            case MONTHS -> StroomDuration.ofDays(duration.getTime() * 31);
            case YEARS -> StroomDuration.ofDays(duration.getTime() * 365);
        };
    }
}
