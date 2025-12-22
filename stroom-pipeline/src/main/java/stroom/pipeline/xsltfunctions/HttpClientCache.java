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

package stroom.pipeline.xsltfunctions;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.pipeline.PipelineConfig;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.io.CloseMode;

import java.util.UUID;

@Singleton
public class HttpClientCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpClientCache.class);

    private static final String CACHE_NAME = "Http Client Cache";

    private final LoadingStroomCache<HttpClientConfiguration, CloseableHttpClient> cache;
    private final HttpClientFactory httpClientFactory;

    @Inject
    public HttpClientCache(final CacheManager cacheManager,
                           final Provider<PipelineConfig> pipelineConfigProvider,
                           final HttpClientFactory httpClientFactory) {
        this.httpClientFactory = httpClientFactory;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> pipelineConfigProvider.get().getHttpClientCache(),
                this::create,
                this::destroy);
    }

    public HttpClient get(final HttpClientConfiguration httpClientConfiguration) {
        return cache.get(httpClientConfiguration);
    }

    private CloseableHttpClient create(final HttpClientConfiguration httpClientConfiguration) {
        LOGGER.debug(() -> "Creating client builder");
        final RuntimeException exception = null;

//            @JsonProperty
//            private final List<String> httpProtocols;

//            @JsonProperty
//            private final StroomDuration readTimeout;
//            @JsonProperty
//            private final StroomDuration writeTimeout;
//            @JsonProperty
//            private final Boolean followRedirects;
//            @JsonProperty
//            private final Boolean followSslRedirects;
//            @JsonProperty
//            private final Boolean retryOnConnectionFailure;


//                                    .
//
//
//            addOptionalConfigurationValue(builder::followRedirects, clientConfig.isFollowRedirects());
//            addOptionalConfigurationValue(builder::followSslRedirects, clientConfig.isFollowSslRedirects());
//            addOptionalConfigurationValue(builder::retryOnConnectionFailure,
//                    clientConfig.isRetryOnConnectionFailure());
//            addOptionalConfigurationDuration(builder::callTimeout, clientConfig.getCallTimeout());
//            addOptionalConfigurationDuration(builder::connectTimeout, clientConfig.getConnectionTimeout());
//            addOptionalConfigurationDuration(builder::readTimeout, clientConfig.getReadTimeout());
//            addOptionalConfigurationDuration(builder::writeTimeout, clientConfig.getWriteTimeout());
//
//            configureHttpProtocolVersions(builder, clientConfig);
//
//            applySslConfig(builder, clientConfig);

        LOGGER.debug(() -> "Creating client");
        return httpClientFactory.get("HttpClientCache-" + UUID.randomUUID(), httpClientConfiguration);
    }

    private void destroy(final HttpClientConfiguration key, final CloseableHttpClient value) {
        LOGGER.debug(() -> "Closing client");
        try {
            value.close(CloseMode.GRACEFUL);
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }
//
//    private void applySslConfig(final Builder builder,
//                                final OkHttpClientConfig clientConfig) {
//        final SSLConfig sslConfig = clientConfig.getSslConfig();
//
//        if (sslConfig != null) {
//            final KeyManager[] keyManagers = createKeyManagers(sslConfig);
//            final TrustManager[] trustManagers = createTrustManagers(sslConfig);
//
//            final SSLContext sslContext;
//            try {
//                sslContext = SSLContext.getInstance(sslConfig.getSslProtocol());
//                sslContext.init(keyManagers, trustManagers, new SecureRandom());
//                builder.sslSocketFactory(sslContext.getSocketFactory(),
//                        (X509TrustManager) trustManagers[0]);
//                if (!sslConfig.isHostnameVerificationEnabled()) {
//                    builder.hostnameVerifier(SSLUtil.PERMISSIVE_HOSTNAME_VERIFIER);
//                }
//            } catch (NoSuchAlgorithmException | KeyManagementException e) {
//                throw ProcessException.create(
//                        "Error initialising SSL context, is the http client configuration valid?. "
//                                + e.getMessage(), e);
//            }
//        }
//    }
//
//    private TrustManager[] createTrustManagers(final SSLConfig sslConfig) {
//        try {
//            return SSLUtil.createTrustManagers(sslConfig, pathCreator);
//        } catch (Exception e) {
//            throw ProcessException.create("Invalid client trustStore configuration: " + e.getMessage(), e);
//        }
//    }
//
//    private KeyManager[] createKeyManagers(final SSLConfig sslConfig) {
//        try {
//            final KeyManager[] keyManagers = SSLUtil.createKeyManagers(sslConfig, pathCreator);
//            return keyManagers;
//        } catch (Exception e) {
//            throw ProcessException.create("Invalid client keyStore configuration: " + e.getMessage(), e);
//        }
//    }
//
//    private <T> void addOptionalConfigurationValue(final Function<T, Builder> builderFunc,
//                                                   final T configValue) {
//        if (configValue != null) {
//            builderFunc.apply(configValue);
//        }
//    }
//
//    private void addOptionalConfigurationDuration(final Function<Duration, Builder> builderFunc,
//                                                  final StroomDuration configValue) {
//        if (configValue != null) {
//            builderFunc.apply(configValue.getDuration());
//        }
//    }
//
//    private void configureHttpProtocolVersions(final Builder builder, OkHttpClientConfig clientConfig) {
//        if (!NullSafe.isEmptyCollection(clientConfig.getHttpProtocols())) {
//            final List<Protocol> protocols = clientConfig.getHttpProtocols()
//                    .stream()
//                    .map(String::toLowerCase)
//                    .map(protocolStr -> {
//                        // No idea why okhttp uses "h2" for http 2.0, so cater for it manually
//                        if ("http/2".equals(protocolStr)) {
//                            return Protocol.HTTP_2;
//                        } else {
//                            try {
//                                return Protocol.get(protocolStr);
//                            } catch (IOException e) {
//                                throw ProcessException.create(LogUtil.message(
//                                        "Invalid http protocol [{}] in client configuration", protocolStr), e);
//                            }
//                        }
//
//                    })
//                    .collect(Collectors.toList());
//            builder.protocols(protocols);
//        }
//    }
}
