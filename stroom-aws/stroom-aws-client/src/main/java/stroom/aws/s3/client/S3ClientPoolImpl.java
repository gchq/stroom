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

package stroom.aws.s3.client;

import stroom.aws.common.AwsCredentialsHelper;
import stroom.aws.s3.shared.AwsHttpConfig;
import stroom.aws.s3.shared.AwsProxyConfig;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.util.cache.CacheConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.crt.S3CrtHttpConfiguration;
import software.amazon.awssdk.services.s3.crt.S3CrtProxyConfiguration;
import software.amazon.awssdk.services.s3.crt.S3CrtRetryConfiguration;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Singleton
public class S3ClientPoolImpl implements S3ClientPool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3ClientPoolImpl.class);
    private static final StroomDuration EXPIRE_AFTER_ACCESS_DURATION = StroomDuration.ofHours(1);
    private static final int MAX_CACHE_SIZE = 100;

    public static final String CACHE_NAME_SYNC = "S3 Client Cache (Sync)";
    public static final String CACHE_NAME_ASYNC = "S3 Client Cache (Async)";

    private final LoadingStroomCache<S3ClientConfig, PooledClientImpl<S3Client>> syncCache;
    private final LoadingStroomCache<S3ClientConfig, PooledClientImpl<S3AsyncClient>> asyncCache;

    @Inject
    public S3ClientPoolImpl(final CacheManager cacheManager) {

        this.syncCache = cacheManager.createLoadingCache(
                CACHE_NAME_SYNC,
                () -> CacheConfig.builder()
                        .maximumSize(MAX_CACHE_SIZE)
                        .expireAfterAccess(EXPIRE_AFTER_ACCESS_DURATION)
                        .build(),
                config -> {
                    final S3Client client = createClient(config);
                    return new PooledClientImpl<>(client, config);
                },
                (s3ClientConfig, pooledClient) -> {
                    LOGGER.debug("Evicting S3Client for s3ClientConfig: {}", s3ClientConfig);
                    pooledClient.setEvicted();
                });

        this.asyncCache = cacheManager.createLoadingCache(
                CACHE_NAME_ASYNC,
                () -> CacheConfig.builder()
                        .maximumSize(MAX_CACHE_SIZE)
                        .expireAfterAccess(EXPIRE_AFTER_ACCESS_DURATION)
                        .build(),
                s3ClientConfig -> {
                    final S3AsyncClient client = createAsyncClient(s3ClientConfig);
                    return new PooledClientImpl<>(client, s3ClientConfig);
                },
                (s3ClientConfig, pooledClient) -> {
                    LOGGER.debug("Evicting S3AsyncClient for s3ClientConfig: {}", s3ClientConfig);
                    pooledClient.setEvicted();
                });
    }

    @Override
    public PooledClient<S3Client> getPooledS3Client(final S3ClientConfig config) {
        final PooledClientImpl<S3Client> pooledClient = syncCache.get(config);
        pooledClient.borrow();
        return pooledClient;
    }

    @Override
    public PooledClient<S3AsyncClient> getPooledS3AsyncClient(final S3ClientConfig config) {
        final PooledClientImpl<S3AsyncClient> pooledClient = asyncCache.get(config);
        pooledClient.borrow();
        return pooledClient;
    }

    private S3AsyncClient createAsyncClient(final S3ClientConfig s3ClientConfig) {
        LOGGER.debug("Creating S3AsyncClient for s3ClientConfig: {}", s3ClientConfig);
        final AwsCredentialsProvider awsCredentialsProvider = createCredentialsProvider(s3ClientConfig);
        return S3AsyncClient
                .crtBuilder()
                .credentialsProvider(awsCredentialsProvider)
                .region(createRegion(s3ClientConfig.getRegion()))
                .minimumPartSizeInBytes(s3ClientConfig.getMinimalPartSizeInBytes())
                .targetThroughputInGbps(s3ClientConfig.getTargetThroughputInGbps())
                .maxConcurrency(s3ClientConfig.getMaxConcurrency())
                .endpointOverride(createUri(s3ClientConfig.getEndpointOverride()))
                .checksumValidationEnabled(s3ClientConfig.getChecksumValidationEnabled())
                .initialReadBufferSizeInBytes(s3ClientConfig.getReadBufferSizeInBytes())
                .httpConfiguration(createHttpConfiguration(s3ClientConfig.getHttpConfiguration()))
                .retryConfiguration(S3CrtRetryConfiguration
                        .builder()
                        .numRetries(s3ClientConfig.getNumRetries())
                        .build())
                .accelerate(s3ClientConfig.getAccelerate())
                .forcePathStyle(s3ClientConfig.getForcePathStyle())
                .crossRegionAccessEnabled(s3ClientConfig.isCrossRegionAccessEnabled())
                .thresholdInBytes(s3ClientConfig.getThresholdInBytes())
                .build();
    }

    private S3Client createClient(final S3ClientConfig s3ClientConfig) {
        LOGGER.debug("Creating S3Client for s3ClientConfig: {}", s3ClientConfig);
        final AwsCredentialsProvider awsCredentialsProvider = createCredentialsProvider(s3ClientConfig);
        return S3Client
                .builder()
                .credentialsProvider(awsCredentialsProvider)
                .region(createRegion(s3ClientConfig.getRegion()))
                .endpointOverride(createUri(s3ClientConfig.getEndpointOverride()))
                .accelerate(s3ClientConfig.getAccelerate())
                .forcePathStyle(s3ClientConfig.getForcePathStyle())
                .crossRegionAccessEnabled(s3ClientConfig.isCrossRegionAccessEnabled())
                .build();
    }

    private URI createUri(final String uri) {
        return NullSafe.mapNonBlankString(uri, URI::create);
    }

    private Region createRegion(final String region) {
        return NullSafe.mapNonBlankString(region, Region::of);
    }

    private S3CrtHttpConfiguration createHttpConfiguration(final AwsHttpConfig awsHttpConfig) {
        if (awsHttpConfig == null) {
            return null;
        }

        return S3CrtHttpConfiguration
                .builder()
                .connectionTimeout(Duration.parse(awsHttpConfig.getConnectionTimeout()))
                .trustAllCertificatesEnabled(awsHttpConfig.getTrustAllCertificatesEnabled())
                .proxyConfiguration(createProxyConfiguration(awsHttpConfig.getProxyConfiguration()))
                .build();
    }

    private S3CrtProxyConfiguration createProxyConfiguration(final AwsProxyConfig awsProxyConfig) {
        if (awsProxyConfig == null) {
            return null;
        }

        return S3CrtProxyConfiguration
                .builder()
                .host(awsProxyConfig.getHost())
                .port(awsProxyConfig.getPort())
                .scheme(awsProxyConfig.getScheme())
                .username(awsProxyConfig.getUsername())
                .password(awsProxyConfig.getPassword())
                .useSystemPropertyValues(awsProxyConfig.getUseSystemPropertyValues())
                .build();
    }

    private AwsCredentialsProvider createCredentialsProvider(final S3ClientConfig s3ClientConfig) {
        return AwsCredentialsHelper.createCredentialsProvider(
                s3ClientConfig.getCredentials(),
                s3ClientConfig.getAssumeRole(),
                s3ClientConfig.getRegion());
    }


    // --------------------------------------------------------------------------------


    private static class PooledClientImpl<T extends AutoCloseable> implements PooledClient<T> {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PooledClientImpl.class);

        private final T client;
        private final S3ClientConfig s3ClientConfig;
        private final AtomicInteger refCounter = new AtomicInteger(0);
        private final AtomicBoolean evicted = new AtomicBoolean(false);
        private final AtomicBoolean closed = new AtomicBoolean(false);

        private PooledClientImpl(final T client, final S3ClientConfig s3ClientConfig) {
            this.client = client;
            this.s3ClientConfig = s3ClientConfig;
        }

        @Override
        public T getClient() {
            return client;
        }

        private void borrow() {
            LOGGER.debug("borrow() - evicted: {}, closed: {}, refCounter: {}, s3ClientConfig: {}",
                    evicted, closed, refCounter, s3ClientConfig);
            if (evicted.get()) {
                throw new IllegalStateException("Cannot borrow in an evicted state");
            }
            if (closed.get()) {
                throw new IllegalStateException("Cannot borrow in a closed state");
            }
            refCounter.incrementAndGet();
        }

        private void setEvicted() {
            LOGGER.debug("setEvicted() - evicted: {}, closed: {}, refCounter: {}, s3ClientConfig: {}",
                    evicted, closed, refCounter, s3ClientConfig);
            // setEvicted() should only be called once by the cache, but just in case
            final boolean didChange = evicted.compareAndSet(false, true);
            if (didChange) {
                if (refCounter.get() == 0) {
                    // It's been evicted but nothing holds a ref to it so close immediately
                    closeClient();
                }
            }
        }

        @Override
        public void close() {
            LOGGER.debug("close() - evicted: {}, closed: {}, refCounter: {}, s3ClientConfig: {}",
                    evicted, closed, refCounter, s3ClientConfig);
            final int newRefCount = refCounter.updateAndGet(count -> count > 0
                    ? count - 1
                    : 0);
            if (newRefCount == 0 && evicted.get()) {
                // This object has been evicted from the cache, and we are the last one to
                // hold a ref to it, so close it.
                closeClient();
            }
        }

        private void closeClient() {
            LOGGER.debug("closeClient() - evicted: {}, closed: {}, refCounter: {}, s3ClientConfig: {}",
                    evicted, closed, refCounter, s3ClientConfig);
            if (closed.compareAndSet(false, true)) {
                try {
                    client.close();
                } catch (final Exception e) {
                    // Swallow the ex as it is not really the caller's problem
                    LOGGER.error(LogUtil.message("Error closing client with config {} - {}",
                            s3ClientConfig, LogUtil.exceptionMessage(e)), e);
                }
            }
        }

        @Override
        public String toString() {
            return "PooledClientImpl{" +
                   "client=" + client +
                   ", s3ClientConfig=" + s3ClientConfig +
                   ", refCounter=" + refCounter +
                   ", evicted=" + evicted +
                   ", closed=" + closed +
                   '}';
        }
    }
}
