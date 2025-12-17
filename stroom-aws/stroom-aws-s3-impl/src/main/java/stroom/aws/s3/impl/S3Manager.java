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
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

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
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class S3Manager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3Manager.class);

    private static final Pattern S3_NAME_PATTERN = Pattern.compile("[^a-z0-9]");
    private static final Pattern S3_BUCKET_NAME_PATTERN = Pattern.compile("[^0-9a-z.]");
    private static final Pattern S3_KEY_NAME_PATTERN = Pattern.compile("[^0-9a-zA-Z!-_.*'()/]");
    private static final Pattern LEADING_HYPHENS = Pattern.compile("^-+");
    private static final Pattern TRAILING_HYPHENS = Pattern.compile("-+$");
    private static final Pattern LEADING_SLASH = Pattern.compile("^/+");
    private static final Pattern TRAILING_SLASH = Pattern.compile("/+$");
    private static final Pattern MULTI_SLASH = Pattern.compile("/+");

    private static final String START_PREFIX = "000";
    private static final int PAD_SIZE = 3;

    private final PathCreator pathCreator;
    private final S3ClientConfig s3ClientConfig;

    public S3Manager(final PathCreator pathCreator,
                     final S3ClientConfig s3ClientConfig) {
        this.pathCreator = pathCreator;
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
        if (uri == null || uri.isBlank()) {
            return null;
        }
        return URI.create(uri);
    }

    private Region createRegion(final String region) {
        if (region == null || region.isBlank()) {
            return null;
        }
        return Region.of(region);
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
        if (s3ClientConfig.getAssumeRole() != null && s3ClientConfig.getAssumeRole().getRequest() != null) {
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
        if (config.getRoleArn() != null) {
            builder.roleArn(config.getRoleArn());
        }
        if (config.getRoleSessionName() != null) {
            builder.roleSessionName(config.getRoleSessionName());
        }
        if (config.getPolicyArns() != null) {
            builder.policyArns(config.getPolicyArns().stream().map(this::createPolicyDescriptorType).toList());
        }
        if (config.getPolicy() != null) {
            builder.policy(config.getPolicy());
        }
        if (config.getDurationSeconds() != null) {
            builder.durationSeconds(config.getDurationSeconds());
        }
        if (config.getTags() != null) {
            builder.tags(config.getTags().stream().map(this::createStsTag).toList());
        }
        if (config.getTransitiveTagKeys() != null) {
            builder.transitiveTagKeys(config.getTransitiveTagKeys());
        }
        if (config.getExternalId() != null) {
            builder.externalId(config.getExternalId());
        }
        if (config.getSerialNumber() != null) {
            builder.serialNumber(config.getSerialNumber());
        }
        if (config.getTokenCode() != null) {
            builder.tokenCode(config.getTokenCode());
        }
        if (config.getSourceIdentity() != null) {
            builder.sourceIdentity(config.getSourceIdentity());
        }
        if (config.getProvidedContexts() != null) {
            builder.providedContexts(config.getProvidedContexts().stream().map(this::createProvidedContext).toList());
        }
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

        if (awsAssumeRoleClientConfig != null && awsAssumeRoleClientConfig.getEndpointOverride() != null) {
            builder.endpointOverride(createUri(awsAssumeRoleClientConfig.getEndpointOverride()));
        }

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
                    return DefaultCredentialsProvider.create();
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
        return DefaultCredentialsProvider.create();
    }

    public String createBucketName(final String bucketNamePattern,
                                   final Meta meta) {
        String bucketName = bucketNamePattern;
        bucketName = pathCreator.replace(bucketName, "feed", meta::getFeedName);
        bucketName = pathCreator.replace(bucketName, "type", meta::getTypeName);
        bucketName = bucketName.toLowerCase(Locale.ROOT);
        bucketName = S3_BUCKET_NAME_PATTERN.matcher(bucketName).replaceAll("-");
        bucketName = LEADING_HYPHENS.matcher(bucketName).replaceAll("");
        bucketName = TRAILING_HYPHENS.matcher(bucketName).replaceAll("");
        if (bucketName.length() > 63) {
            LOGGER.warn("Truncating bucket name: " + bucketName);
            return bucketName.substring(0, 63);
        }

        return bucketName;
    }

    private String createS3Name(final String name) {
        String s3Name = name;
        s3Name = s3Name.toLowerCase(Locale.ROOT);
        s3Name = S3_NAME_PATTERN.matcher(s3Name).replaceAll("-");
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
        String keyName = keyPattern;
        final ZonedDateTime zonedDateTime =
                ZonedDateTime.ofInstant(Instant.ofEpochMilli(meta.getCreateMs()), ZoneOffset.UTC);
        final String idPadded = padId(meta.getId());
        keyName = pathCreator.replaceTimeVars(keyName, zonedDateTime);
        keyName = pathCreator.replace(keyName, "feed", meta::getFeedName);
        keyName = pathCreator.replace(keyName, "type", meta::getTypeName);
        keyName = pathCreator.replace(keyName, "id", () -> String.valueOf(meta.getId()));
        keyName = pathCreator.replace(keyName, "idPath", () -> getIdPath(idPadded));
        keyName = pathCreator.replace(keyName, "idPadded", () -> idPadded);

        keyName = S3_KEY_NAME_PATTERN.matcher(keyName).replaceAll("-");
        keyName = MULTI_SLASH.matcher(keyName).replaceAll("/");
        keyName = LEADING_SLASH.matcher(keyName).replaceAll("");
        keyName = TRAILING_SLASH.matcher(keyName).replaceAll("");

        if (keyName.getBytes(StandardCharsets.UTF_8).length > 1024) {
            throw new RuntimeException("Key name too long: " + keyName);
        }

        return keyName;
    }

    /**
     * Pad a prefix.
     */
    private String padId(final Long current) {
        if (current == null) {
            return START_PREFIX;
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(current);

        while ((sb.length() % PAD_SIZE) != 0) {
            sb.insert(0, "0");
        }

        return sb.toString();
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
                .collect(Collectors.toMap(e -> createS3Name(e.getKey()), Entry::getValue));

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

    private String getDebugIdentity(final String bucketName,
                                    final String key) {
        return "bucketName=" +
               bucketName +
               Optional.ofNullable(key).map(k -> ", key=" + k).orElse("");
    }
}
