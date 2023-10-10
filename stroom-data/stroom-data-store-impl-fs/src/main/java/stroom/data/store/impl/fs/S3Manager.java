package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.AwsHttpConfig;
import stroom.data.store.impl.fs.shared.AwsProfileCredentials;
import stroom.data.store.impl.fs.shared.AwsProxyConfig;
import stroom.data.store.impl.fs.shared.S3ClientConfig;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
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
import software.amazon.awssdk.transfer.s3.S3TransferManager;
import software.amazon.awssdk.transfer.s3.model.CompletedFileDownload;
import software.amazon.awssdk.transfer.s3.model.CompletedFileUpload;
import software.amazon.awssdk.transfer.s3.model.DownloadFileRequest;
import software.amazon.awssdk.transfer.s3.model.FileDownload;
import software.amazon.awssdk.transfer.s3.model.FileUpload;
import software.amazon.awssdk.transfer.s3.model.UploadFileRequest;
import software.amazon.awssdk.transfer.s3.progress.LoggingTransferListener;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class S3Manager {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3Manager.class);

    private static final Pattern S3_NAME_PATTERN = Pattern.compile("[^a-z0-9]");

    private final S3ClientConfig s3ClientConfig;

    public S3Manager(final S3ClientConfig s3ClientConfig) {
        this.s3ClientConfig = s3ClientConfig;
    }

    private S3AsyncClient createAsyncClient(final S3ClientConfig s3ClientConfig) {
        final AwsCredentialsProvider awsCredentialsProvider = createCredentialsProvider(s3ClientConfig);
        return S3AsyncClient
                .crtBuilder()
                .credentialsProvider(awsCredentialsProvider)
                .region(Region.of(s3ClientConfig.getRegion()))
                .minimumPartSizeInBytes(s3ClientConfig.getMinimalPartSizeInBytes())
                .targetThroughputInGbps(s3ClientConfig.getTargetThroughputInGbps())
                .maxConcurrency(s3ClientConfig.getMaxConcurrency())
                .endpointOverride(URI.create(s3ClientConfig.getEndpointOverride()))
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
                .region(Region.of(s3ClientConfig.getRegion()))
                .endpointOverride(URI.create(s3ClientConfig.getEndpointOverride()))
                .accelerate(s3ClientConfig.getAccelerate())
                .forcePathStyle(s3ClientConfig.getForcePathStyle())
                .crossRegionAccessEnabled(s3ClientConfig.isCrossRegionAccessEnabled())
                .build();
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
        AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
        if (s3ClientConfig.getCredentialsProviderType() != null) {
            switch (s3ClientConfig.getCredentialsProviderType()) {
                case ANONYMOUS -> credentialsProvider = AnonymousCredentialsProvider.create();
                case DEFAULT -> credentialsProvider = DefaultCredentialsProvider.create();
                case ENVIRONMENT_VARIABLE -> EnvironmentVariableCredentialsProvider.create();
//            case    LAZY -> LazyAwsCredentialsProvider.create();
//            case    PROCESS -> ProcessCredentialsProvider.create();
                case PROFILE -> {
                    final stroom.data.store.impl.fs.shared.AwsCredentials awsCredentials =
                            s3ClientConfig.getCredentials();
                    if (awsCredentials instanceof final AwsProfileCredentials awsProfileCredentials) {
                        if (awsProfileCredentials.getProfileFilePath() != null &&
                                awsProfileCredentials.getProfileFilePath().length() > 0) {
                            final Path path = Paths.get(awsProfileCredentials.getProfileFilePath());
                            credentialsProvider = ProfileCredentialsProvider
                                    .builder()
                                    .profileFile(ProfileFile.builder().content(path).build())
                                    .build();
                        } else {
                            credentialsProvider = ProfileCredentialsProvider
                                    .builder()
                                    .profileName(awsProfileCredentials.getProfileName())
                                    .build();
                        }
                    } else {
                        credentialsProvider = ProfileCredentialsProvider.create();
                    }
                }
                case STATIC -> {
                    final stroom.data.store.impl.fs.shared.AwsCredentials awsCredentials =
                            s3ClientConfig.getCredentials();
                    if (awsCredentials instanceof
                            final stroom.data.store.impl.fs.shared.AwsBasicCredentials awsBasicCredentials) {
                        final AwsCredentials credentials = AwsBasicCredentials
                                .create(awsBasicCredentials.getAccessKeyId(), awsBasicCredentials.getSecretAccessKey());
                        credentialsProvider = StaticCredentialsProvider.create(credentials);
                    } else if (awsCredentials instanceof
                            final stroom.data.store.impl.fs.shared.AwsSessionCredentials awsSessionCredentials) {
                        final AwsSessionCredentials credentials = AwsSessionCredentials
                                .builder()
                                .accessKeyId(awsSessionCredentials.getAccessKeyId())
                                .secretAccessKey(awsSessionCredentials.getSecretAccessKey())
                                .sessionToken(awsSessionCredentials.getSessionToken())
                                .build();
                        credentialsProvider = StaticCredentialsProvider.create(credentials);
                    } else {
                        throw new RuntimeException("No credentials");
                    }
                }
                case SYSTEM_PROPERTY -> SystemPropertyCredentialsProvider.create();
                case WEB -> {
                    final stroom.data.store.impl.fs.shared.AwsCredentials awsCredentials =
                            s3ClientConfig.getCredentials();
                    if (awsCredentials instanceof
                            final stroom.data.store.impl.fs.shared.AwsWebCredentials awsWebCredentials) {
                        credentialsProvider = WebIdentityTokenFileCredentialsProvider
                                .builder()
                                .roleArn(awsWebCredentials.getRoleArn())
                                .roleSessionName(awsWebCredentials.getRoleSessionName())
                                .webIdentityTokenFile(Paths.get(awsWebCredentials.getWebIdentityTokenFile()))
                                .asyncCredentialUpdateEnabled(awsWebCredentials.getAsyncCredentialUpdateEnabled())
                                .prefetchTime(Duration.parse(awsWebCredentials.getPrefetchTime()))
                                .staleTime(Duration.parse(awsWebCredentials.getStaleTime()))
                                .roleSessionDuration(Duration.parse(awsWebCredentials.getSessionDuration()))
                                .build();
                    } else {
                        throw new RuntimeException("No credentials");
                    }
                }
            }
        }

        return credentialsProvider;
    }

    public String createBucketName(final Meta meta) {
        if (s3ClientConfig.isUseFeedAsBucketName()) {
            final String bucketName = createS3Name(meta.getFeedName()) + "." + createS3Name(meta.getTypeName());
            if (bucketName.length() > 63) {
                return bucketName.substring(0, 63);
            }
            return bucketName;
        }

        final String bucketName = s3ClientConfig.getDefaultBucketName();
        if (bucketName == null || bucketName.isBlank()) {
            throw new RuntimeException("No bucket name defined in S3 volume config");
        }
        return bucketName;
    }

    private String createS3Name(final String name) {
        return S3_NAME_PATTERN.matcher(name.toLowerCase(Locale.ROOT)).replaceAll("-");
    }

    public PutObjectResponse upload(final Meta meta,
                                    final AttributeMap attributeMap,
                                    final Path source) {
        final String bucketName = createBucketName(meta);
        final String key = createKey(meta);

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

    public PutObjectResponse tryUpload(final String bucketName,
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
        final String bucketName = createBucketName(meta);
        final String key = createKey(meta);
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
        final String bucketName = createBucketName(meta);
        final String key = createKey(meta);
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

    public String createKey(final Meta meta) {
//        String extension = EXTENSION_MAP.get(streamType);
//        if (extension == null) {
//            extension = "dat";
//        }
//        if (s3ClientConfig.isUseFeedAsBucketName()) {
//            return meta.getId() + ".zip";
//        }

        final String utcDate = DateUtil.createNormalDateTimeString(meta.getCreateMs());
        final String paddedId = FsPrefixUtil.padId(meta.getId());
        return createS3Name(meta.getFeedName()) +
                "/" +
                createS3Name(meta.getTypeName()) +
                "/" +
                createS3Name(utcDate.substring(0, 4)) +
                "/" +
                createS3Name(utcDate.substring(5, 7)) +
                "/" +
                createS3Name(utcDate.substring(8, 10)) +
                "/" +
                paddedId +
                S3FileExtensions.ZIP_EXTENSION;
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
