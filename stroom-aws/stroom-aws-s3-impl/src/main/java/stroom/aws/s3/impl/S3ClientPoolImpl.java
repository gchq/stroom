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

package stroom.aws.s3.impl;


import stroom.aws.s3.shared.AwsAssumeRole;
import stroom.aws.s3.shared.AwsAssumeRoleClientConfig;
import stroom.aws.s3.shared.AwsAssumeRoleRequest;
import stroom.aws.s3.shared.AwsHttpConfig;
import stroom.aws.s3.shared.AwsPolicyDescriptorType;
import stroom.aws.s3.shared.AwsProvidedContext;
import stroom.aws.s3.shared.AwsProxyConfig;
import stroom.aws.s3.shared.AwsTag;
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
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.crt.S3CrtHttpConfiguration;
import software.amazon.awssdk.services.s3.crt.S3CrtProxyConfiguration;
import software.amazon.awssdk.services.s3.crt.S3CrtRetryConfiguration;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.StsAsyncClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.PolicyDescriptorType;
import software.amazon.awssdk.services.sts.model.ProvidedContext;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
        return NullSafe.isNonBlankString(uri)
                ? URI.create(uri)
                : null;
    }

    private Region createRegion(final String region) {
        return NullSafe.isNonBlankString(region)
                ? Region.of(region)
                : null;
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
        if (NullSafe.nonNull(s3ClientConfig.getAssumeRole(), AwsAssumeRole::getRequest)) {
            // If the config asks the client to assume a role then get assumed role credentials.
            try (final StsAsyncClient stsAsyncClient =
                    createStsAsyncClient(s3ClientConfig)) {
                final AssumeRoleRequest assumeRoleRequest =
                        createAssumeRoleRequest(s3ClientConfig.getAssumeRole().getRequest());
                final Future<AssumeRoleResponse> responseFuture = stsAsyncClient.assumeRole(assumeRoleRequest);
                final AssumeRoleResponse response = responseFuture.get();
                final Credentials credentials = response.credentials();
                final AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                        credentials.accessKeyId(),
                        credentials.secretAccessKey(),
                        credentials.sessionToken());
                return AwsCredentialsProviderChain.builder()
                        .credentialsProviders(StaticCredentialsProvider.create(sessionCredentials))
                        .build();
            } catch (final InterruptedException | ExecutionException | RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw new RuntimeException(e.getMessage());
            }
        }

        return createCredentialsProvider(s3ClientConfig.getCredentials());
    }

    private AssumeRoleRequest createAssumeRoleRequest(final AwsAssumeRoleRequest config) {
        final AssumeRoleRequest.Builder builder = AssumeRoleRequest.builder();
        NullSafe.consume(config.getRoleArn(), builder::roleArn);
        NullSafe.consume(config.getRoleSessionName(), builder::roleSessionName);
        NullSafe.consume(config.getPolicyArns(), policyArns ->
                builder.policyArns(policyArns.stream()
                        .map(this::createPolicyDescriptorType)
                        .toList()));
        NullSafe.consume(config.getPolicy(), builder::policy);
        NullSafe.consume(config.getDurationSeconds(), builder::durationSeconds);
        NullSafe.consume(config.getTags(), tags ->
                builder.tags(tags.stream()
                        .map(this::createStsTag)
                        .toList()));
        NullSafe.consume(config.getTransitiveTagKeys(), builder::transitiveTagKeys);
        NullSafe.consume(config.getExternalId(), builder::externalId);
        NullSafe.consume(config.getSerialNumber(), builder::serialNumber);
        NullSafe.consume(config.getTokenCode(), builder::tokenCode);
        NullSafe.consume(config.getSourceIdentity(), builder::sourceIdentity);
        NullSafe.consume(config.getProvidedContexts(), providedContexts ->
                builder.providedContexts(providedContexts.stream()
                        .map(this::createProvidedContext)
                        .toList()));
        return builder.build();
    }

    private ProvidedContext createProvidedContext(final AwsProvidedContext awsProvidedContext) {
        return ProvidedContext
                .builder()
                .contextAssertion(awsProvidedContext.getContextAssertion())
                .providerArn(awsProvidedContext.getProviderArn())
                .build();
    }

    private PolicyDescriptorType createPolicyDescriptorType(final AwsPolicyDescriptorType awsPolicyDescriptorType) {
        return PolicyDescriptorType.builder().arn(awsPolicyDescriptorType.getArn()).build();
    }

    private software.amazon.awssdk.services.sts.model.Tag createStsTag(final AwsTag awsTag) {
        return software.amazon.awssdk.services.sts.model.Tag
                .builder()
                .key(awsTag.getKey())
                .value(awsTag.getValue())
                .build();
    }

    private StsAsyncClient createStsAsyncClient(final S3ClientConfig s3ClientConfig) {
        final StsAsyncClientBuilder builder = StsAsyncClient.builder();

        final AwsAssumeRole assumeRole = s3ClientConfig.getAssumeRole();
        final AwsAssumeRoleClientConfig awsAssumeRoleClientConfig = assumeRole.getClientConfig();
        if (awsAssumeRoleClientConfig != null && awsAssumeRoleClientConfig.getCredentials() != null) {
            builder.credentialsProvider(createCredentialsProvider(awsAssumeRoleClientConfig.getCredentials()));
        } else if (s3ClientConfig.getCredentials() != null) {
            builder.credentialsProvider(createCredentialsProvider(s3ClientConfig.getCredentials()));
        }

        if (awsAssumeRoleClientConfig != null && awsAssumeRoleClientConfig.getRegion() != null) {
            builder.region(createRegion(awsAssumeRoleClientConfig.getRegion()));
        } else if (s3ClientConfig.getRegion() != null) {
            builder.region(createRegion(s3ClientConfig.getRegion()));
        }

        NullSafe.consume(awsAssumeRoleClientConfig,
                AwsAssumeRoleClientConfig::getEndpointOverride,
                endpointOverride ->
                        builder.endpointOverride(createUri(endpointOverride)));

        return builder.build();
    }

    private AwsCredentialsProvider createCredentialsProvider(
            final stroom.aws.s3.shared.AwsCredentials awsCredentials) {

        if (awsCredentials != null) {
            switch (awsCredentials) {
                case final stroom.aws.s3.shared.AwsAnonymousCredentials awsAnonymousCredentials -> {
                    LOGGER.debug("Using AWS anonymous credentials");
                    return AnonymousCredentialsProvider.create();
                }
                case final stroom.aws.s3.shared.AwsBasicCredentials awsBasicCredentials -> {
                    LOGGER.debug("Using AWS basic credentials");
                    final AwsCredentials credentials = AwsBasicCredentials
                            .create(awsBasicCredentials.getAccessKeyId(), awsBasicCredentials.getSecretAccessKey());
                    return StaticCredentialsProvider.create(credentials);

                }
                case final stroom.aws.s3.shared.AwsDefaultCredentials awsDefaultCredentials -> {
                    LOGGER.debug("Using AWS default credentials");
                    return DefaultCredentialsProvider.builder().build();
                }
                case final stroom.aws.s3.shared.AwsEnvironmentVariableCredentials awsEnvironmentVariableCredentials -> {
                    LOGGER.debug("Using AWS environment variable credentials");
                    return EnvironmentVariableCredentialsProvider.create();
                }
                case final stroom.aws.s3.shared.AwsProfileCredentials awsProfileCredentials -> {
                    LOGGER.debug("Using AWS profile credentials");
                    if (!NullSafe.isBlankString(awsProfileCredentials.getProfileFilePath())) {
                        final Path path = Paths.get(awsProfileCredentials.getProfileFilePath());
                        return ProfileCredentialsProvider
                                .builder()
                                .profileFile(ProfileFile.builder().content(path).build())
                                .build();
                    } else {
                        return ProfileCredentialsProvider
                                .builder()
                                .profileName(awsProfileCredentials.getProfileName())
                                .build();
                    }
                }
                case final stroom.aws.s3.shared.AwsSessionCredentials awsSessionCredentials -> {
                    LOGGER.debug("Using AWS session credentials");
                    final AwsSessionCredentials credentials = AwsSessionCredentials
                            .builder()
                            .accessKeyId(awsSessionCredentials.getAccessKeyId())
                            .secretAccessKey(awsSessionCredentials.getSecretAccessKey())
                            .sessionToken(awsSessionCredentials.getSessionToken())
                            .build();
                    return StaticCredentialsProvider.create(credentials);

                }
                case final stroom.aws.s3.shared.AwsSystemPropertyCredentials awsSystemPropertyCredentials -> {
                    LOGGER.debug("Using AWS system property credentials");
                    return SystemPropertyCredentialsProvider.create();
                }
                case final stroom.aws.s3.shared.AwsWebCredentials awsWebCredentials -> {
                    LOGGER.debug("Using AWS web identity credentials");
                    return WebIdentityTokenFileCredentialsProvider
                            .builder()
                            .roleArn(awsWebCredentials.getRoleArn())
                            .roleSessionName(awsWebCredentials.getRoleSessionName())
                            .webIdentityTokenFile(Paths.get(awsWebCredentials.getWebIdentityTokenFile()))
                            .asyncCredentialUpdateEnabled(awsWebCredentials.getAsyncCredentialUpdateEnabled())
                            .prefetchTime(Duration.parse(awsWebCredentials.getPrefetchTime()))
                            .staleTime(Duration.parse(awsWebCredentials.getStaleTime()))
                            .roleSessionDuration(Duration.parse(awsWebCredentials.getSessionDuration()))
                            .build();
                }
                default -> {
                    final String message = "Unknown AWS credentials type: " + awsCredentials.getClass().getName();
                    LOGGER.error(() -> message);
                    throw new RuntimeException(message);
                }
            }
        }

        LOGGER.debug("No AWS credentials provided, using default");
        return DefaultCredentialsProvider.builder().build();
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

//        public int getRefCount() {
//            return refCounter.get();
//        }

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
            final int refCount = refCounter.decrementAndGet();
            if (refCount == 0 && evicted.get()) {
                // This object has been evicted from the cache, and we are the last one to
                // hold a ref to it, so close it.
                closeClient();
            }
        }

        private void closeClient() {
            LOGGER.debug("closeClient() - evicted: {}, closed: {}, refCounter: {}, s3ClientConfig: {}",
                    evicted, closed, refCounter, s3ClientConfig);
            if (!closed.get()) {
                try {
                    closed.set(true);
                    client.close();
                } catch (final Exception e) {
                    // Swallow the ex as it is not really the caller's problem
                    LOGGER.error(LogUtil.message("Error closing client with config {} - {}",
                            s3ClientConfig, LogUtil.exceptionMessage(e)), e);
                }
            }
        }
    }


    // --------------------------------------------------------------------------------


//    interface PooledItem extends AutoCloseable {
//
//        int getRefCount();
//
//    }
}
