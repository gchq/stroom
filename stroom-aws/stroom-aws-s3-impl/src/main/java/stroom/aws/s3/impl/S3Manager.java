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
import stroom.cache.api.TemplateCache;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;
import stroom.util.shared.string.CIKey;
import stroom.util.string.StringIdUtil;
import stroom.util.string.TemplateUtil.Template;

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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.crt.S3CrtHttpConfiguration;
import software.amazon.awssdk.services.s3.crt.S3CrtProxyConfiguration;
import software.amazon.awssdk.services.s3.crt.S3CrtRetryConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Request;
import software.amazon.awssdk.services.s3.model.S3Response;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.StsAsyncClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.PolicyDescriptorType;
import software.amazon.awssdk.services.sts.model.ProvidedContext;
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class S3Manager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3Manager.class);

    private static final Pattern S3_META_KEY_INVALID_CHARS_PATTERN = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern S3_BUCKET_NAME_INVALID_CHARS_PATTERN = Pattern.compile("[^0-9a-z.-]");
    private static final Pattern S3_KEY_NAME_INVALID_CHARS_PATTERN = Pattern.compile("[^0-9a-zA-Z!-_.*'()/]");
    private static final Pattern LEADING_HYPHENS = Pattern.compile("^-+");
    private static final Pattern TRAILING_HYPHENS = Pattern.compile("-+$");
    private static final Pattern LEADING_SLASH = Pattern.compile("^/+");
    private static final Pattern TRAILING_SLASH = Pattern.compile("/+$");
    private static final Pattern MULTI_SLASH = Pattern.compile("/+");

    private static final String START_PREFIX = "000";
    private static final int PAD_SIZE = 3;

    private static final CIKey FEED_VAR = CIKey.internStaticKey("feed");
    private static final CIKey TYPE_VAR = CIKey.internStaticKey("type");
    private static final CIKey ID_VAR = CIKey.internStaticKey("id");
    private static final CIKey ID_PATH_VAR = CIKey.internStaticKey("idPath");
    private static final CIKey ID_PADDED_VAR = CIKey.internStaticKey("idPadded");
    private static final String SEPARATE_META_FILE_METADATA_KEY = "has-stroom-meta-file";

    static final String AWS_USER_DEFINED_META_PREFIX = "x-amz-meta-";
    static final String MANIFEST_METADATA_KEY_PREFIX = "mf-";
    static final String META_METADATA_KEY_PREFIX = "meta-";

    private final TemplateCache templateCache;
    private final S3ClientConfig s3ClientConfig;

    public S3Manager(final TemplateCache templateCache,
                     final S3ClientConfig s3ClientConfig) {
        this.templateCache = templateCache;
        this.s3ClientConfig = s3ClientConfig;
    }

    private S3AsyncClient createAsyncClient(final S3ClientConfig s3ClientConfig) {
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

    public String createBucketName(final String bucketNamePattern,
                                   final Meta meta) {
        final Template template = templateCache.getTemplate(bucketNamePattern);
        String bucketName = template.buildExecutor()
                .addLazyReplacement(FEED_VAR, meta::getFeedName)
                .addLazyReplacement(TYPE_VAR, meta::getTypeName)
                .execute();

        bucketName = cleanBuckName(bucketName);
        final int len = bucketName.length();
        if (len < 3) {
            LOGGER.error("Bucket name too short, must be >=3. bucketName: '{}'", bucketName);
            throw new RuntimeException(LogUtil.message("Bucket name too short, must be >=3. bucketName: '{}'",
                    bucketName));
        } else if (len > 63) {
            LOGGER.warn("Truncating bucket name: '{}'. Length must be >=3 and <=63.", bucketName);
            return bucketName.substring(0, 63);
        }

        return bucketName;
    }

    private static String cleanBuckName(String bucketName) {
        bucketName = bucketName.toLowerCase(Locale.ROOT);
        bucketName = S3_BUCKET_NAME_INVALID_CHARS_PATTERN.matcher(bucketName).replaceAll("-");
        bucketName = LEADING_HYPHENS.matcher(bucketName).replaceAll("");
        bucketName = TRAILING_HYPHENS.matcher(bucketName).replaceAll("");
        return bucketName;
    }

    private String createManifestKey(final String key) {
        return MANIFEST_METADATA_KEY_PREFIX + cleanS3MetaDataKey(key);
    }

    private String createMetaKey(final String key, final int part) {
        return part + "-" + cleanS3MetaDataKey(key);
    }

    private String cleanS3MetaDataKey(final String metaKey) {
        String s3Name = metaKey;
        s3Name = s3Name.toLowerCase(Locale.ROOT);
        s3Name = S3_META_KEY_INVALID_CHARS_PATTERN.matcher(s3Name).replaceAll("-");
        s3Name = LEADING_HYPHENS.matcher(s3Name).replaceAll("");
        s3Name = TRAILING_HYPHENS.matcher(s3Name).replaceAll("");
        return s3Name;
    }

    public String getBucketNamePattern() {
        return NullSafe
                .nonBlank(s3ClientConfig.getBucketName())
                .orElse(S3ClientConfig.DEFAULT_BUCKET_NAME);
    }

    public String getKeyNamePattern() {
        return NullSafe
                .nonBlank(s3ClientConfig.getKeyPattern())
                .orElse(S3ClientConfig.DEFAULT_KEY_PATTERN);
    }

    public PutObjectResponse upload(final Meta meta,
                                    final AttributeMap attributeMap,
                                    final Path source) {
        return upload(getBucketNamePattern(), getKeyNamePattern(), meta, attributeMap, source);
    }

    public PutObjectResponse upload(final String bucketNamePattern,
                                    final String keyNamePattern,
                                    final Meta meta,
                                    final AttributeMap attributeMap,
                                    final Path source) {
        final String bucketName = createBucketName(bucketNamePattern, meta);
        final String key = createKey(keyNamePattern, meta);

        try {
            return tryUpload(bucketName, key, meta, attributeMap, source);
        } catch (final RuntimeException e) {
            if (s3ClientConfig.isCreateBuckets()) {
                debug("Error uploading: ", bucketName, key, e);

                // If we are creating buckets then try to create the bucket and upload again.
                try {
                    createBucket(bucketName);
                    return tryUpload(bucketName, key, meta, attributeMap, source);
                } catch (final RuntimeException e2) {
                    error("Error uploading: ", bucketName, key, e2);
                    throw e2;
                }
            } else {
                error("Error uploading: ", bucketName, key, e);
                throw e;
            }
        }
    }

    private PutObjectResponse tryUpload(final String bucketName,
                                        final String key,
                                        final Meta meta,
                                        final AttributeMap attributeMap,
                                        final Path source) {
        final PutObjectRequest request = createPutObjectRequest(bucketName, key, meta, attributeMap);
        logRequest("Uploading: ", bucketName, key, request);

        final PutObjectResponse response;
        if (s3ClientConfig.isAsync()) {
            try (final S3AsyncClient s3AsyncClient = createAsyncClient(s3ClientConfig)) {
                if (s3ClientConfig.isMultipart()) {
                    try (final S3TransferManager transferManager =
                            S3TransferManager.builder()
                                    .s3Client(s3AsyncClient)
                                    .build()) {

                        final UploadFileRequest uploadFileRequest =
                                UploadFileRequest.builder()
                                        .putObjectRequest(request)
                                        .addTransferListener(LoggingTransferListener.create())
                                        .source(source)
                                        .build();

                        final FileUpload fileUpload = transferManager.uploadFile(uploadFileRequest);

                        final CompletedFileUpload uploadResult = fileUpload.completionFuture().join();
                        LOGGER.debug(() -> "Upload result: " +
                                           getDebugIdentity(bucketName, key) +
                                           ", result=" +
                                           uploadResult);
                        response = uploadResult.response();
                    }

                } else {
                    response = s3AsyncClient.putObject(request, source).join();
                }
            }
        } else {
            try (final S3Client s3Client = createClient(s3ClientConfig)) {
                response = s3Client.putObject(request, source);
            }
        }

        logResponse("Uploaded: ", bucketName, key, response);
        return response;
    }

    private void createBucket(final String bucketName) {
        final CreateBucketRequest request = CreateBucketRequest.builder().bucket(bucketName).build();
        logRequest("Creating bucket: ", bucketName, null, request);

        final CreateBucketResponse response;
        if (s3ClientConfig.isAsync()) {
            try (final S3AsyncClient s3AsyncClient = createAsyncClient(s3ClientConfig)) {
                response = s3AsyncClient.createBucket(
                        request).join();
            } catch (final S3Exception e) {
                error("Error creating bucket: ", bucketName, null, e);
                throw e;
            }
        } else {
            try (final S3Client s3Client = createClient(s3ClientConfig)) {
                response = s3Client.createBucket(request);
            } catch (final S3Exception e) {
                error("Error creating bucket: ", bucketName, null, e);
                throw e;
            }
        }

        logResponse("Created bucket: ", bucketName, null, response);
    }

    /**
     * Get part of an S3 object, defined by a contiguous byte range.
     *
     * @param meta            The {@link Meta} the object belongs to.
     * @param childStreamType The child stream type, or null if this is not a child stream.
     * @param byteRange       The range of bytes to fetch.
     * @return The repose containing the byte range.
     */
    public ResponseInputStream<GetObjectResponse> getObject(final Meta meta,
                                                            final String childStreamType,
                                                            final Range<Long> byteRange) {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(byteRange);
        final String bucketName = createBucketName(getBucketNamePattern(), meta);
        final String key = createKey(getKeyNamePattern(), meta, childStreamType);
        final GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .range(rangeToHttpString(byteRange))
                .build();

        logRequest("GET (range) : ", bucketName, key, byteRange, request);

        try (final S3Client s3Client = createClient(s3ClientConfig)) {
            return s3Client.getObject(request);
        } catch (final RuntimeException e) {
            error("Error getting: ", bucketName, key, byteRange, e);
            throw e;
        }
    }

    /**
     * Get part of an S3 object, defined by a contiguous byte range.
     *
     * @param meta            The {@link Meta} the object belongs to.
     * @param childStreamType The child stream type, or null if this is not a child stream.
     * @param byteRange       The range of bytes to fetch.
     * @return The repose containing the byte range.
     */
    public ResponseInputStream<GetObjectResponse> getByteRange(final Meta meta,
                                                               final String childStreamType,
                                                               final Range<Long> byteRange) {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(byteRange);
        final String bucketName = createBucketName(getBucketNamePattern(), meta);
        final String key = createKey(getKeyNamePattern(), meta, childStreamType);
        final GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .range(rangeToHttpString(byteRange))
                .build();

        logRequest("GET (range) : ", bucketName, key, byteRange, request);

        try (final S3Client s3Client = createClient(s3ClientConfig)) {
            return s3Client.getObject(request);
        } catch (final RuntimeException e) {
            error("Error getting: ", bucketName, key, byteRange, e);
            throw e;
        }
    }

    public long getFileSize(final Meta meta, final String childStreamType) {
        Objects.requireNonNull(meta);
        final String bucketName = createBucketName(getBucketNamePattern(), meta);
        final String key = createKey(getKeyNamePattern(), meta);
        final HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        logRequest("HEAD: ", bucketName, key, request);

        try (final S3Client s3Client = createClient(s3ClientConfig)) {
            final HeadObjectResponse headObjectResponse = s3Client.headObject(request);
            return Objects.requireNonNullElse(headObjectResponse.contentLength(), 0L);
        } catch (final RuntimeException e) {
            error("Error getting file size: ", bucketName, key, e);
            throw e;
        }
    }

    public S3ObjectInfo getObjectInfo(final Meta meta, final String childStreamType) {
        Objects.requireNonNull(meta);
        final String bucketName = createBucketName(getBucketNamePattern(), meta);
        final String key = createKey(getKeyNamePattern(), meta);
        final HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        logRequest("HEAD: ", bucketName, key, request);

        try (final S3Client s3Client = createClient(s3ClientConfig)) {
            final HeadObjectResponse headObjectResponse = s3Client.headObject(request);
            final Map<String, String> metadata = headObjectResponse.metadata();
            final S3ObjectInfo s3ObjectInfo;

            if (NullSafe.hasEntries(metadata)) {
                final AttributeMap manifest = readManifest(metadata);
                final List<AttributeMap> attributeMaps = readMeta(metadata);

                s3ObjectInfo = new S3ObjectInfo(
                        bucketName,
                        key,
                        attributeMaps,
                        manifest,
                        false);
            } else {
                s3ObjectInfo = new S3ObjectInfo(
                        bucketName,
                        key,
                        Collections.emptyList(),
                        new AttributeMap(),
                        false);
            }

            return s3ObjectInfo;

        } catch (final RuntimeException e) {
            error("Error downloading: ", bucketName, key, e);
            throw e;
        }
    }

    private List<AttributeMap> readMeta(final Map<String, String> metadata) {
        final Map<Integer, List<SegmentedMetaEntry>> map = metadata.entrySet()
                .stream()
                .map(S3Manager::buildMetaEntry)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(SegmentedMetaEntry::segmentIdx));

        return map.entrySet()
                .stream()
                .sorted(Entry.comparingByKey())
                .map(Entry::getValue)
                .map(segmentedMetaEntries -> {
                    final Map<String, String> metaMap = segmentedMetaEntries.stream()
                            .collect(Collectors.toMap(SegmentedMetaEntry::key, SegmentedMetaEntry::value));
                    return new AttributeMap(metaMap);
                })
                .toList();
    }

    /**
     * Pkg private for testing
     */
    static SegmentedMetaEntry buildMetaEntry(final Entry<String, String> metaEntry) {
        // metaDataKey is like
        String key = metaEntry.getKey();
        // Remove AWS user-defined meta key prefix if present
        removeAwsPrefix(key);

        final SegmentedMetaEntry segmentedMetaEntry;
        if (key.startsWith(META_METADATA_KEY_PREFIX)) {
            // Remove our own prefix
            key = key.substring(META_METADATA_KEY_PREFIX.length());

            final int dashIdx = key.indexOf("-");
            if (dashIdx != -1) {
                final String segmentIdxStr = key.substring(0, dashIdx);
                try {
                    final int segmentIdx = Integer.parseInt(segmentIdxStr);
                    key = key.substring(dashIdx + 1);
                    return new SegmentedMetaEntry(segmentIdx, key, metaEntry.getValue());
                } catch (final NumberFormatException e) {
                    LOGGER.warn("Ignoring meta entry entry {}. Unable to parse '{}'.", metaEntry, segmentIdxStr);
                    segmentedMetaEntry = null;
                }
            } else {
                LOGGER.warn("Ignoring meta entry entry {}. No '-' found.", metaEntry);
                segmentedMetaEntry = null;
            }
        } else {
            segmentedMetaEntry = null;
        }
        return segmentedMetaEntry;
    }

    private AttributeMap readManifest(final Map<String, String> metadata) {
        if (NullSafe.hasEntries(metadata)) {
            return new AttributeMap(metadata.entrySet()
                    .stream()
                    .map(entry -> {
                        String key = entry.getKey();
                        removeAwsPrefix(key);
                        if (key.startsWith(MANIFEST_METADATA_KEY_PREFIX)) {
                            key = key.substring(MANIFEST_METADATA_KEY_PREFIX.length());
                            return Map.entry(key, entry.getValue());
                        } else {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue)));
        } else {
            return new AttributeMap();
        }
    }

    private static String removeAwsPrefix(final String key) {
        return NullSafe.get(
                key,
                k -> {
                    if (k.startsWith(AWS_USER_DEFINED_META_PREFIX)) {
                        k = k.substring(AWS_USER_DEFINED_META_PREFIX.length());
                    }
                    return k;
                });
    }

    private String rangeToHttpString(final Range<Long> range) {
        Objects.requireNonNull(range);
        if (!range.isBounded()) {
            throw new IllegalArgumentException("Range must be bounded, range: " + range);
        }
        final long toInc = range.getTo() - 1;
        if (range.getFrom() > toInc) {
            throw new IllegalArgumentException("Invalid range: " + range);
        }
        final String str = "bytes=" + range.getFrom() + "-" + toInc;
        LOGGER.debug("rangeToHttpString() - returning: {}", str);
        return str;
    }

    public GetObjectResponse download(final Meta meta,
                                      final Path dest) {
        final String bucketName = createBucketName(getBucketNamePattern(), meta);
        final String key = createKey(getKeyNamePattern(), meta);
        final GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        logRequest("Downloading: ", bucketName, key, request);

        final GetObjectResponse response;
        if (s3ClientConfig.isAsync()) {
            try (final S3AsyncClient s3AsyncClient = createAsyncClient(s3ClientConfig)) {
                if (s3ClientConfig.isMultipart()) {
                    try (final S3TransferManager transferManager =
                            S3TransferManager.builder()
                                    .s3Client(s3AsyncClient)
                                    .build()) {

                        final DownloadFileRequest downloadFileRequest =
                                DownloadFileRequest.builder()
                                        .getObjectRequest(request)
                                        .addTransferListener(LoggingTransferListener.create())
                                        .destination(dest)
                                        .build();

                        final FileDownload downloadFile = transferManager.downloadFile(downloadFileRequest);

                        final CompletedFileDownload downloadResult = downloadFile.completionFuture().join();
                        LOGGER.debug(() -> "Download result: " +
                                           getDebugIdentity(bucketName, key) +
                                           ", result=" +
                                           downloadResult);
                        response = downloadResult.response();
                    }
                } else {
                    response = s3AsyncClient.getObject(request, dest).join();
                }
            } catch (final RuntimeException e) {
                error("Error downloading: ", bucketName, key, e);
                throw e;
            }
        } else {
            try (final S3Client s3Client = createClient(s3ClientConfig)) {
                response = s3Client.getObject(request, dest);
            } catch (final RuntimeException e) {
                error("Error downloading: ", bucketName, key, e);
                throw e;
            }
        }

        logResponse("Downloaded: ", bucketName, key, response);
        return response;
    }

    public DeleteObjectResponse delete(final Meta meta) {
        final String bucketName = createBucketName(getBucketNamePattern(), meta);
        final String key = createKey(getKeyNamePattern(), meta);
        final DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        logRequest("Deleting: ", bucketName, key, request);

        final DeleteObjectResponse response;
        if (s3ClientConfig.isAsync()) {
            try (final S3AsyncClient s3AsyncClient = createAsyncClient(s3ClientConfig)) {
                response = s3AsyncClient.deleteObject(request).join();
            } catch (final S3Exception e) {
                error("Error deleting: ", bucketName, key, e);
                throw e;
            }
        } else {
            try (final S3Client s3Client = createClient(s3ClientConfig)) {
                response = s3Client.deleteObject(request);
            } catch (final S3Exception e) {
                error("Error deleting: ", bucketName, key, e);
                throw e;
            }
        }

        logResponse("Deleted: ", bucketName, key, response);
        return response;
    }

    private Tagging createTags(final Meta meta) {
        return Tagging.builder()
                .tagSet(
                        Tag.builder().key("feed").value(meta.getFeedName()).build(),
                        Tag.builder().key("stream-type").value(meta.getTypeName()).build(),
                        Tag.builder().key("meta-id").value(String.valueOf(meta.getId())).build()
                )
                .build();
    }

    public String createKey(final String keyPattern, final Meta meta) {
        return createKey(keyPattern, meta, null);
    }

    public String createKey(final String keyPattern, final Meta meta, final String childStreamType) {
        final Template template = templateCache.getTemplate(keyPattern);
        final ZonedDateTime zonedDateTime =
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(meta.getCreateMs()), ZoneOffset.UTC);

        String keyName = template.buildExecutor()
                .addStandardTimeReplacements(zonedDateTime)
                .addLazyReplacement(FEED_VAR, meta::getFeedName)
                .addLazyReplacement(TYPE_VAR, meta::getTypeName)
                .addLazyReplacement(ID_VAR, () -> String.valueOf(meta.getId()))
                .addLazyReplacement(ID_PATH_VAR, () -> getIdPath(padId(meta.getId())))
                .addLazyReplacement(ID_PADDED_VAR, () -> padId(meta.getId()))
                .execute();

        keyName = cleanKeyName(keyName);

        final int keyBytesLen = keyName.getBytes(StandardCharsets.UTF_8).length;
        if (keyBytesLen > 1024) {
            throw new RuntimeException(LogUtil.message("Key name '{}' too long {}, must be less than 1,024 bytes",
                    keyName, keyBytesLen));
        }

        return keyName;
    }

    private static String cleanKeyName(String keyName) {
        keyName = S3_KEY_NAME_INVALID_CHARS_PATTERN.matcher(keyName).replaceAll("-");
        keyName = MULTI_SLASH.matcher(keyName).replaceAll("/");
        keyName = LEADING_SLASH.matcher(keyName).replaceAll("");
        keyName = TRAILING_SLASH.matcher(keyName).replaceAll("");
        return keyName;
    }

    /**
     * Pad a prefix.
     */
    private String padId(final Long current) {
        if (current == null) {
            return START_PREFIX;
        } else {
            return StringIdUtil.idToString(current);
        }
    }

    private String getIdPath(final String id) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < id.length() - PAD_SIZE; i += PAD_SIZE) {
            final String part = id.substring(i, i + PAD_SIZE);
            if (!sb.isEmpty()) {
                sb.append("/");
            }
            sb.append(part);
        }
        return sb.toString();
    }

    private PutObjectRequest createPutObjectRequest(final String bucketName,
                                                    final String key,
                                                    final Meta meta,
                                                    final AttributeMap attributeMap) {
        final Map<String, String> metadata = attributeMap
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e ->
                                cleanS3MetaDataKey(e.getKey()),
                        Entry::getValue));

        return PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .tagging(createTags(meta))
                .metadata(metadata)
                .build();
    }

    private void logRequest(final String message,
                            final String bucketName,
                            final String key,
                            final S3Request request) {
        LOGGER.debug(() -> message + getDebugIdentity(bucketName, key));
        LOGGER.trace(() -> message + getDebugIdentity(bucketName, key) + ", request=" + request);
    }

    private void logRequest(final String message,
                            final String bucketName,
                            final String key,
                            final Range<Long> range,
                            final S3Request request) {
        LOGGER.debug(() -> message + getDebugIdentity(bucketName, key) + ", range=" + range);
        LOGGER.trace(() -> message + getDebugIdentity(bucketName, key) + ", range=" + range + ", request=" + request);
    }

    private void logResponse(final String message,
                             final String bucketName,
                             final String key,
                             final S3Response response) {
        LOGGER.debug(() -> message + getDebugIdentity(bucketName, key));
        LOGGER.trace(() -> message + getDebugIdentity(bucketName, key) + ", response=" + response);
    }

    private void debug(final String message,
                       final String bucketName,
                       final String key,
                       final Exception e) {
        LOGGER.debug(() -> message +
                           getDebugIdentity(bucketName, key) +
                           ", message=" +
                           e.getMessage(), e);
    }

    private void error(final String message,
                       final String bucketName,
                       final String key,
                       final Exception e) {
        LOGGER.error(() -> message +
                           getDebugIdentity(bucketName, key) +
                           ", message=" +
                           e.getMessage(), e);
    }

    private void error(final String message,
                       final String bucketName,
                       final String key,
                       final Range<Long> range,
                       final Exception e) {
        LOGGER.error(() -> message +
                           getDebugIdentity(bucketName, key) +
                           ", range=" + range +
                           ", message=" + e.getMessage(), e);
    }

    private String getDebugIdentity(final String bucketName,
                                    final String key) {
        return "bucketName=" +
               bucketName +
               Optional.ofNullable(key).map(k -> ", key=" + k).orElse("");
    }


    // --------------------------------------------------------------------------------


    record S3ObjectInfo(
            String bucketName,
            String key,
            List<AttributeMap> meta,
            AttributeMap manifest,
            boolean hasMetaFile) {

    }


    // --------------------------------------------------------------------------------


    record SegmentedMetaEntry(int segmentIdx, String key, String value) {

    }
}
