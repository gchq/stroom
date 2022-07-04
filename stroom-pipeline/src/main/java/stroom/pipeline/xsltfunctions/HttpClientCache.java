/*
 * Copyright 2016 Crown Copyright
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
import stroom.cache.api.ICache;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.util.NullSafe;
import stroom.util.cert.SSLConfig;
import stroom.util.cert.SSLUtil;
import stroom.util.config.OkHttpClientConfig;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.OkHttpClient.Builder;
import okhttp3.Protocol;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Singleton
public class HttpClientCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpClientCache.class);

    private static final String CACHE_NAME = "Http Client Cache";

    private final ICache<String, OkHttpClient> cache;
    private final PathCreator pathCreator;

    @Inject
    public HttpClientCache(final CacheManager cacheManager,
                           final PipelineConfig pipelineConfig,
                           final PathCreator pathCreator) {
        this.pathCreator = pathCreator;
        cache = cacheManager.create(CACHE_NAME, pipelineConfig::getHttpClientCache, this::create);
    }

    public OkHttpClient get(final String clientConfig) {
        return cache.get(clientConfig);
    }

    private OkHttpClient create(final String clientConfigStr) {
        LOGGER.debug(() -> "Creating client builder");
        final OkHttpClient.Builder builder = new OkHttpClient.Builder();

        if (clientConfigStr != null && !clientConfigStr.isBlank()) {
            final OkHttpClientConfig clientConfig;

            try {
                clientConfig = new ObjectMapper()
                        .readerFor(OkHttpClientConfig.class)
                        .readValue(clientConfigStr);
            } catch (IOException e) {
                throw new ProcessException(LogUtil.message(
                        "Error parsing HTTP client configuration \"{}\". {}", clientConfigStr, e.getMessage()), e);
            }

            addOptionalConfigurationValue(builder::followRedirects, clientConfig.getFollowRedirects());
            addOptionalConfigurationValue(builder::followSslRedirects, clientConfig.getFollowSslRedirects());
            addOptionalConfigurationValue(builder::retryOnConnectionFailure,
                    clientConfig.getRetryOnConnectionFailure());
            addOptionalConfigurationDuration(builder::callTimeout, clientConfig.getCallTimeout());
            addOptionalConfigurationDuration(builder::connectTimeout, clientConfig.getConnectionTimeout());
            addOptionalConfigurationDuration(builder::readTimeout, clientConfig.getReadTimeout());
            addOptionalConfigurationDuration(builder::writeTimeout, clientConfig.getWriteTimeout());

            configureHttpProtocolVersions(builder, clientConfig);

            applySslConfig(builder, clientConfig);
        }

        LOGGER.debug(() -> "Creating client");
        return builder.build();
    }

    private void applySslConfig(final Builder builder,
                                final OkHttpClientConfig clientConfig) {
        final SSLConfig sslConfig = clientConfig.getSslConfig();

        if (sslConfig != null) {
            final KeyManager[] keyManagers = createKeyManagers(sslConfig);
            final TrustManager[] trustManagers = createTrustManagers(sslConfig);

            final SSLContext sslContext;
            try {
                sslContext = SSLContext.getInstance(sslConfig.getSslProtocol());
                sslContext.init(keyManagers, trustManagers, new SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(),
                        (X509TrustManager) trustManagers[0]);
                if (!sslConfig.isHostnameVerificationEnabled()) {
                    builder.hostnameVerifier(SSLUtil.PERMISSIVE_HOSTNAME_VERIFIER);
                }
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new ProcessException(
                        "Error initialising SSL context, is the http client configuration valid?. "
                                + e.getMessage(), e);
            }
        }
    }

    private TrustManager[] createTrustManagers(final SSLConfig sslConfig) {
        try {
            return SSLUtil.createTrustManagers(sslConfig, pathCreator);
        } catch (Exception e) {
            throw new ProcessException("Invalid client trustStore configuration: " + e.getMessage(), e);
        }
    }

    private KeyManager[] createKeyManagers(final SSLConfig sslConfig) {
        try {
            final KeyManager[] keyManagers = SSLUtil.createKeyManagers(sslConfig, pathCreator);
            return keyManagers;
        } catch (Exception e) {
            throw new ProcessException("Invalid client keyStore configuration: " + e.getMessage(), e);
        }
    }

    private <T> void addOptionalConfigurationValue(final Function<T, Builder> builderFunc,
                                                   final T configValue) {
        if (configValue != null) {
            builderFunc.apply(configValue);
        }
    }

    private void addOptionalConfigurationDuration(final Function<Duration, Builder> builderFunc,
                                                  final StroomDuration configValue) {
        if (configValue != null) {
            builderFunc.apply(configValue.getDuration());
        }
    }

    private void configureHttpProtocolVersions(final Builder builder, OkHttpClientConfig clientConfig) {
        if (!NullSafe.isEmptyCollection(clientConfig.getHttpProtocols())) {
            final List<Protocol> protocols = clientConfig.getHttpProtocols()
                    .stream()
                    .map(String::toLowerCase)
                    .map(protocolStr -> {
                        // No idea why okhttp uses "h2" for http 2.0, so cater for it manually
                        if ("http/2".equals(protocolStr)) {
                            return Protocol.HTTP_2;
                        } else {
                            try {
                                return Protocol.get(protocolStr);
                            } catch (IOException e) {
                                throw new ProcessException(LogUtil.message(
                                        "Invalid http protocol [{}] in client configuration", protocolStr), e);
                            }
                        }

                    })
                    .collect(Collectors.toList());
            builder.protocols(protocols);
        }
    }
}
