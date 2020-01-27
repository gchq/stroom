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

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.pipeline.PipelineConfig;
import stroom.util.cert.SSLConfig;
import stroom.util.cert.SSLUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Singleton
public class HttpClientCache {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpClientCache.class);

    private static final String CACHE_NAME = "Http Client Cache";

    private final ICache<String, OkHttpClient> cache;

    @Inject
    public HttpClientCache(final CacheManager cacheManager, final PipelineConfig pipelineConfig) {
        cache = cacheManager.create(CACHE_NAME, pipelineConfig::getHttpClientCache, this::create);
    }

    public OkHttpClient get(final String clientConfig) {
        return cache.get(clientConfig);
    }

    private OkHttpClient create(final String clientConfig) {
        try {
            LOGGER.debug(() -> "Creating client builder");
            OkHttpClient.Builder builder = new OkHttpClient.Builder();

            if (clientConfig != null && !clientConfig.isEmpty()) {
                final SSLConfig sslConfig = new ObjectMapper()
                        .readerFor(SSLConfig.class)
                        .readValue(clientConfig);

                final KeyManager[] keyManagers = SSLUtil.createKeyManagers(sslConfig);
                final TrustManager[] trustManagers = SSLUtil.createTrustManagers(sslConfig);

                final SSLContext sslContext;
                try {
                    sslContext = SSLContext.getInstance(sslConfig.getSslProtocol());
                    sslContext.init(keyManagers, trustManagers, new SecureRandom());
                    builder = builder.sslSocketFactory(sslContext.getSocketFactory(),
                            (X509TrustManager) trustManagers[0]);
                    if (!sslConfig.isHostnameVerificationEnabled()) {
                        builder = builder.hostnameVerifier(SSLUtil.PERMISSIVE_HOSTNAME_VERIFIER);
                    }
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    throw new RuntimeException("Error initialising SSL context", e);
                }
            }

            LOGGER.debug(() -> "Creating client");
            return builder.build();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
