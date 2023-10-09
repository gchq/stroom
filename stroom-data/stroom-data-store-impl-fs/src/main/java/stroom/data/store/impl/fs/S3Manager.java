package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.shared.AwsHttpConfig;
import stroom.data.store.impl.fs.shared.AwsProfileCredentials;
import stroom.data.store.impl.fs.shared.AwsProxyConfig;
import stroom.data.store.impl.fs.shared.S3ClientConfig;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
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
import software.amazon.awssdk.core.exception.SdkClientException;
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
//
//    private static final Map<String, String> EXTENSION_MAP = new HashMap<>();
//
//    static {
//        EXTENSION_MAP.put(InternalStreamTypeNames.SEGMENT_INDEX, "seg");
//        EXTENSION_MAP.put(InternalStreamTypeNames.BOUNDARY_INDEX, "bdy");
//
//        // Child types
//        EXTENSION_MAP.put(InternalStreamTypeNames.MANIFEST, "mf");
//        EXTENSION_MAP.put(StreamTypeNames.META, "meta");
//        EXTENSION_MAP.put(StreamTypeNames.CONTEXT, "ctx");
//    }

    private static final Pattern S3_NAME_PATTERN = Pattern.compile("[^a-z1-9]");

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
                    final stroom.data.store.impl.fs.shared.AwsCredentials awsCredentials = s3ClientConfig.getCredentials();
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

    private String createBucketName(final Meta meta) {
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
                                    final String streamType,
                                    final AttributeMap attributeMap,
                                    final Path source) {
        final String bucketName = createBucketName(meta);

        final PutObjectRequest request = createPutObjectRequest(bucketName, meta, streamType, attributeMap);
        final PutObjectResponse response;
        LOGGER.debug(() -> "Uploading: " + request);

        if (s3ClientConfig.isAsync()) {
            if (s3ClientConfig.isCreateBuckets()) {
                createBucketAsync(bucketName);
            }

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
                        LOGGER.debug(() -> "Upload result: " + uploadResult);
                        response = uploadResult.response();
                    }

                } else {
                    response = s3AsyncClient.putObject(request, source).join();
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        } else {
            if (s3ClientConfig.isCreateBuckets()) {
                createBucket(bucketName);
            }

            try (final S3Client s3Client = createClient(s3ClientConfig)) {
                response = s3Client.putObject(request, source);
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }

        LOGGER.debug(() -> "Upload response: " + response);
        return response;
    }

    private CreateBucketResponse createBucketAsync(final String bucketName) {
        try (final S3AsyncClient s3AsyncClient = createAsyncClient(s3ClientConfig)) {
            return s3AsyncClient.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();
        } catch (final S3Exception e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private CreateBucketResponse createBucket(final String bucketName) {
        try (final S3Client s3Client = createClient(s3ClientConfig)) {
            return s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } catch (final S3Exception e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    private String uploadMultipart(final Meta meta,
                                   final String streamType,
                                   final AttributeMap attributeMap,
                                   final Path source,
                                   final String bucketName) {
        final S3AsyncClient s3AsyncClient = S3AsyncClient
                .crtBuilder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        "AKIAIOSFODNN7EXAMPLE",
                        "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")))
//                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.EU_NORTH_1)
                .endpointOverride(URI.create("http://localhost:9444"))
//                .targetThroughputInGbps(20.0)
//                .minimumPartSizeInBytes(1L)
                .build();

        try (final S3TransferManager transferManager =
                S3TransferManager.builder()
                        .s3Client(s3AsyncClient)
                        .build()) {

            final PutObjectRequest request = createPutObjectRequest(bucketName, meta, streamType, attributeMap);
            LOGGER.debug(() -> "Uploading: " + request);

            final UploadFileRequest uploadFileRequest =
                    UploadFileRequest.builder()
                            .putObjectRequest(request)
                            .addTransferListener(LoggingTransferListener.create())
                            .source(source)
                            .build();

            final FileUpload fileUpload = transferManager.uploadFile(uploadFileRequest);

            final CompletedFileUpload uploadResult = fileUpload.completionFuture().join();
            System.out.println(uploadResult.response());
            return uploadResult.response().toString();
        }
    }

    private void uploadAsync(final Meta meta,
                             final String streamType,
                             final AttributeMap attributeMap,
                             final Path source,
                             final String bucketName) {
        final S3AsyncClient s3AsyncClient = S3AsyncClient
                .crtBuilder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        "AKIAIOSFODNN7EXAMPLE",
                        "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY")))
//                .credentialsProvider(DefaultCredentialsProvider.create())
                .region(Region.US_WEST_2)
                .endpointOverride(URI.create("http://localhost:9444"))
//                .targetThroughputInGbps(20.0)
//                .minimumPartSizeInBytes(ByteSize.ofMebibytes(8).getBytes())
                .build();


        s3AsyncClient.createBucket(CreateBucketRequest.builder().bucket(bucketName).build()).join();

        final PutObjectRequest request = createPutObjectRequest(bucketName, meta, streamType, attributeMap);
        System.out.println("Uploading: " + request);
        s3AsyncClient.putObject(request, source).join();
    }

    private void uploadSync(final S3Client s3Client,
                            final Meta meta,
                            final String streamType,
                            final AttributeMap attributeMap,
                            final Path source,
                            final String bucketName) {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());

        final PutObjectRequest request = createPutObjectRequest(bucketName, meta, streamType, attributeMap);
        System.out.println("Uploading: " + request);
        s3Client.putObject(request, source);
    }

    public GetObjectResponse download(final Meta meta,
                                      final String streamType,
                                      final Path dest) {
        final String bucketName = createBucketName(meta);
        final GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(createKey(meta, streamType))
                .build();
        final GetObjectResponse response;
        LOGGER.debug(() -> "Downloading: " + request);

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
                        LOGGER.debug(() -> "Download result: " + downloadResult);
                        response = downloadResult.response();
                    }

                } else {
                    response = s3AsyncClient.getObject(request, dest).join();
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        } else {
            try (final S3Client s3Client = createClient(s3ClientConfig)) {
                response = s3Client.getObject(request, dest);
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }

        LOGGER.debug(() -> "Download response: " + response);
        return response;
    }

    public DeleteObjectResponse delete(final Meta meta,
                                       final String streamType) {
        final String bucketName = createBucketName(meta);
        final DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(createKey(meta, streamType))
                .build();
        final DeleteObjectResponse response;
        LOGGER.debug(() -> "Deleting: " + request);

        if (s3ClientConfig.isAsync()) {
            try (final S3AsyncClient s3AsyncClient = createAsyncClient(s3ClientConfig)) {
                response = s3AsyncClient.deleteObject(request).join();
            } catch (final S3Exception e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        } else {
            try (final S3Client s3Client = createClient(s3ClientConfig)) {
                response = s3Client.deleteObject(request);
            } catch (final S3Exception e) {
                LOGGER.error(e::getMessage, e);
                throw e;
            }
        }

        LOGGER.debug(() -> "Delete response: " + response);
        return response;
    }

    public Optional<DeleteObjectResponse> tryDelete(final Meta meta,
                                                    final String streamType) {
        try {
            return Optional.of(delete(meta, streamType));
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
        return Optional.empty();
    }

    private Tagging createTags(final Meta meta, final String streamType) {
        return Tagging.builder()
                .tagSet(
                        Tag.builder().key("feed").value(meta.getFeedName()).build(),
                        Tag.builder().key("stream-type").value(streamType).build(),
                        Tag.builder().key("meta-id").value(String.valueOf(meta.getId())).build()
                )
                .build();
    }

    private String createKey(final Meta meta, final String streamType) {
//        String extension = EXTENSION_MAP.get(streamType);
//        if (extension == null) {
//            extension = "dat";
//        }
        if (s3ClientConfig.isUseFeedAsBucketName()) {
            return meta.getId() + ".zip";
        }
        return meta.getFeedName() + "/" + meta.getTypeName() + "/" + meta.getId() + ".zip";
    }

    private PutObjectRequest createPutObjectRequest(final String bucketName,
                                                    final Meta meta,
                                                    final String streamType,
                                                    final AttributeMap attributeMap) {
        final Map<String, String> metadata = attributeMap
                .entrySet()
                .stream()
                .collect(Collectors.toMap(e -> createS3Name(e.getKey()), Entry::getValue));

        return PutObjectRequest.builder()
                .bucket(bucketName)
                .key(createKey(meta, streamType))
                .tagging(createTags(meta, streamType))
                .metadata(metadata)
                .build();
    }
}