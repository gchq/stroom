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

import stroom.aws.s3.shared.S3ClientConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;
import stroom.util.shared.string.CIKey;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest.Builder;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class S3ClientHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3ClientHelper.class);

    public static final String ZSTD_CONTENT_ENCODING = "zstd";

    private final S3ClientConfig s3ClientConfig;
    private final S3ClientPool s3ClientPool;

    public S3ClientHelper(final S3ClientConfig s3ClientConfig,
                          final S3ClientPool clientPool) {
        this.s3ClientConfig = s3ClientConfig;
        this.s3ClientPool = clientPool;
    }

    private PooledClient<S3AsyncClient> getAsyncClient() {
        return s3ClientPool.getPooledS3AsyncClient(s3ClientConfig);
    }

    private PooledClient<S3Client> getSyncClient() {
        return s3ClientPool.getPooledS3Client(s3ClientConfig);
    }

    public PutObjectResponse upload(final String bucketName,
                                    final String key,
                                    final Map<String, String> tags,
                                    final Map<CIKey, String> s3Metadata,
                                    final Path sourceFile) {
        NullSafe.requireNonBlankString(key, () -> "key must not be blank");
        NullSafe.requireNonBlankString(bucketName, () -> "bucketName must not be blank");
        Objects.requireNonNull(sourceFile);

        try {
            return tryUpload(bucketName, key, tags, s3Metadata, sourceFile);
        } catch (final RuntimeException e) {
            if (s3ClientConfig.isCreateBuckets()) {
                debug("Error uploading: ", bucketName, key, sourceFile, e);

                // If we are creating buckets then try to create the bucket and upload again.
                try {
                    createBucket(bucketName);
                    return tryUpload(bucketName, key, tags, s3Metadata, sourceFile);
                } catch (final RuntimeException e2) {
                    error("Error uploading: ", bucketName, key, sourceFile, e2);
                    throw e2;
                }
            } else {
                error("Error uploading: ", bucketName, key, sourceFile, e);
                throw e;
            }
        }
    }

    private PutObjectResponse tryUpload(final String bucketName,
                                        final String key,
                                        final Map<String, String> tags,
                                        final Map<CIKey, String> s3Metadata,
                                        final Path sourceFile) {
        final PutObjectRequest request = createPutObjectRequest(bucketName, key, tags, s3Metadata, sourceFile);
        logRequest("Uploading: ", bucketName, key, request);

        final PutObjectResponse response;
        if (s3ClientConfig.isAsync()) {
            response = s3ClientPool.getWithAsyncS3Client(s3ClientConfig, s3AsyncClient -> {
                if (s3ClientConfig.isMultipart()) {
                    try (final S3TransferManager transferManager =
                            S3TransferManager.builder()
                                    .s3Client(s3AsyncClient)
                                    .build()) {

                        final UploadFileRequest uploadFileRequest =
                                UploadFileRequest.builder()
                                        .putObjectRequest(request)
                                        .addTransferListener(LoggingTransferListener.create())
                                        .source(sourceFile)
                                        .build();

                        final FileUpload fileUpload = transferManager.uploadFile(uploadFileRequest);

                        final CompletedFileUpload uploadResult = fileUpload.completionFuture().join();
                        LOGGER.debug(() -> "Upload result: " +
                                           getDebugIdentity(bucketName, key) +
                                           ", result=" +
                                           uploadResult);
                        return uploadResult.response();
                    } catch (final RuntimeException e) {
                        debug("Error putting object (async, multi-part)", bucketName, key, sourceFile, e);
                        throw e;
                    }
                } else {
                    try {
                        return s3AsyncClient.putObject(request, sourceFile).join();
                    } catch (final Exception e) {
                        debug("Error putting object (async)", bucketName, key, sourceFile, e);
                        throw e;
                    }
                }
            });
        } else {
            response = s3ClientPool.getWithS3Client(s3ClientConfig, s3Client -> {
                try {
                    LOGGER.debug(() -> LogUtil.message(
                            "tryUpload() - Putting Object (sync) - bucketName: {}, key: {}, source: {}, " +
                            "s3Metadata: {}, requestMeta: {}, tags: {}",
                            bucketName,
                            key,
                            sourceFile.toAbsolutePath(),
                            s3Metadata,
                            request.metadata(),
                            request.tagging()));
                    return s3Client.putObject(request, sourceFile);
                } catch (final Exception e) {
                    debug("Error putting object (sync)", bucketName, key, sourceFile, e);
                    throw e;
                }
            });
        }

        logResponse("Uploaded: ", bucketName, key, response);
        return response;
    }

    private void createBucket(final String bucketName) {
        final CreateBucketRequest request = CreateBucketRequest.builder()
                .bucket(bucketName)
                .build();
        logRequest("Creating bucket: ", bucketName, null, request);

        final CreateBucketResponse response;
        if (s3ClientConfig.isAsync()) {
            try (final PooledClient<S3AsyncClient> pooledClient = getAsyncClient()) {
                response = pooledClient.getClient()
                        .createBucket(request)
                        .join();
            } catch (final S3Exception e) {
                error("Error creating bucket: ", bucketName, null, e);
                throw e;
            }
        } else {
            try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
                response = pooledClient.getClient().createBucket(request);
            } catch (final S3Exception e) {
                error("Error creating bucket: ", bucketName, null, e);
                throw e;
            }
        }

        logResponse("Created bucket: ", bucketName, null, response);
    }

    public ResponseInputStream<GetObjectResponse> getObject(final String bucketName,
                                                            final String key) {
        NullSafe.requireNonBlankString(bucketName);
        NullSafe.requireNonBlankString(key);
        final GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        logRequest("GET: ", bucketName, key, request);

        try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
            return pooledClient.getClient().getObject(request);
        } catch (final RuntimeException e) {
            error("Error getting: ", bucketName, key, e);
            throw e;
        }
    }

    /**
     * Get part of an S3 object, defined by a contiguous byte range.
     *
     * @param byteRange The range of bytes to fetch.
     * @return The repose containing the byte range.
     */
    public ResponseInputStream<GetObjectResponse> getObjectByteRange(final String bucketName,
                                                                     final String key,
                                                                     final Range<Long> byteRange) {
        NullSafe.requireNonBlankString(bucketName, () -> "bucketName must not be blank");
        NullSafe.requireNonBlankString(key, () -> "key must not be blank");
        Objects.requireNonNull(byteRange);
        final GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .range(rangeToHttpString(byteRange))
                .build();

        logRequest("GET (range) : ", bucketName, key, byteRange, request);

        try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
            return LOGGER.logDurationIfDebugEnabled(
                    () -> pooledClient.getClient().getObject(request),
                    () -> LogUtil.message("getByteRange() - bucket: '{}', key: '{}', byteRange: '{}'",
                            bucketName, key, byteRange));
        } catch (final RuntimeException e) {
            error("Error getting: ", bucketName, key, byteRange, e);
            throw e;
        }
    }

    public long getFileSize(final String bucketName,
                            final String key) {
        NullSafe.requireNonBlankString(bucketName, () -> "bucketName must not be blank");
        NullSafe.requireNonBlankString(key, () -> "key must not be blank");
        final HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        logRequest("HEAD: ", bucketName, key, request);

        try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
            final HeadObjectResponse headObjectResponse = LOGGER.logDurationIfDebugEnabled(
                    () -> pooledClient.getClient().headObject(request),
                    () -> LogUtil.message("getFileSize() - bucket: '{}', key: '{}'",
                            bucketName, key));
            return Objects.requireNonNullElse(headObjectResponse.contentLength(), 0L);
        } catch (final RuntimeException e) {
            error("Error getting file size: ", bucketName, key, e);
            throw e;
        }
    }

    public S3ObjectInfo getObjectInfo(final String bucketName,
                                      final String key) {
        NullSafe.requireNonBlankString(key, () -> "key must not be blank");
        NullSafe.requireNonBlankString(bucketName, () -> "bucketName must not be blank");
        final HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        logRequest("HEAD: ", bucketName, key, request);

        try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
            final HeadObjectResponse headObjectResponse = LOGGER.logDurationIfDebugEnabled(
                    () -> pooledClient.getClient().headObject(request),
                    () -> LogUtil.message("getObjectInfo() - bucket: '{}', key: '{}'",
                            bucketName, key));

            final Map<String, String> s3Metadata = headObjectResponse.metadata();
            final long contentLength = NullSafe.getLong(headObjectResponse.contentLength());

            return new S3ObjectInfo(
                    bucketName,
                    key,
                    contentLength,
                    NullSafe.getInt(headObjectResponse.tagCount()),
                    CIKey.mapOf(s3Metadata));
        } catch (final NoSuchKeyException e) {
            error("Error getting object info: ", bucketName, key, e);
            throw new RuntimeException(LogUtil.message("No data found for using key: {}, bucket: {}",
                    key, bucketName), e);
        } catch (final RuntimeException e) {
            error("Error getting object info: ", bucketName, key, e);
            throw e;
        }
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

    public GetObjectResponse download(final String bucketName,
                                      final String key,
                                      final Path downloadDestination,
                                      final boolean allowAsync) {
        NullSafe.requireNonBlankString(bucketName, () -> "bucketName must not be blank");
        NullSafe.requireNonBlankString(key, () -> "key must not be blank");
        Objects.requireNonNull(downloadDestination);
        final GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        logRequest("Downloading: ", bucketName, key, request);

        final GetObjectResponse response;
        if (allowAsync && s3ClientConfig.isAsync()) {
            try (final PooledClient<S3AsyncClient> pooledClient = getAsyncClient()) {
                final S3AsyncClient s3AsyncClient = pooledClient.getClient();
                if (s3ClientConfig.isMultipart()) {
                    try (final S3TransferManager transferManager =
                            S3TransferManager.builder()
                                    .s3Client(s3AsyncClient)
                                    .build()) {

                        final DownloadFileRequest downloadFileRequest =
                                DownloadFileRequest.builder()
                                        .getObjectRequest(request)
                                        .addTransferListener(LoggingTransferListener.create())
                                        .destination(downloadDestination)
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
                    response = s3AsyncClient.getObject(request, downloadDestination).join();
                }
            } catch (final RuntimeException e) {
                error("Error downloading: ", bucketName, key, e);
                throw e;
            }
        } else {
            try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
                response = LOGGER.logDurationIfDebugEnabled(
                        () -> pooledClient.getClient().getObject(request, downloadDestination),
                        () -> LogUtil.message("Download() - bucket: '{}', key: '{}', dest: '{}'",
                                bucketName, key, downloadDestination));
            } catch (final RuntimeException e) {
                error("Error downloading: ", bucketName, key, e);
                throw e;
            }
        }

        logResponse("Downloaded: ", bucketName, key, response);
        return response;
    }

    public List<String> listKeys(final String bucketName,
                                 final String keyPrefix) {
        NullSafe.requireNonBlankString(bucketName, () -> "bucketName must not be blank");
        Objects.requireNonNull(keyPrefix);
        final ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(keyPrefix)
                .build();

        try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
            final ListObjectsV2Response listObjectsV2Response = pooledClient.getClient()
                    .listObjectsV2(listObjectsV2Request);
            final List<String> keys = NullSafe.stream(listObjectsV2Response.contents())
                    .map(S3Object::key)
                    .toList();
            LOGGER.debug(() -> LogUtil.message(
                    "listKeys() - bucketName: '{}', keyPrefix: '{}', keys.size: '{}', keys: {}",
                    bucketName, keyPrefix, NullSafe.size(keys), LogUtil.getSample(keys, 5)));
            return keys;
        } catch (final S3Exception e) {
            error("Error listing keys: bucket: '{}', keyPrefix: '{}'", bucketName, keyPrefix, e);
            throw e;
        }
    }

    public DeleteObjectResponse delete(final String bucketName,
                                       final String key) {
        NullSafe.requireNonBlankString(bucketName, () -> "bucketName must not be blank");
        NullSafe.requireNonBlankString(key, () -> "key must not be blank");
        final DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        logRequest("Deleting: ", bucketName, key, request);

        final DeleteObjectResponse response;
        if (s3ClientConfig.isAsync()) {
            try (final PooledClient<S3AsyncClient> pooledClient = getAsyncClient()) {
                response = pooledClient.getClient().deleteObject(request).join();
            } catch (final S3Exception e) {
                error("Error deleting: ", bucketName, key, e);
                throw e;
            }
        } else {
            try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
                response = pooledClient.getClient().deleteObject(request);
            } catch (final S3Exception e) {
                error("Error deleting: ", bucketName, key, e);
                throw e;
            }
        }

        logResponse("Deleted: ", bucketName, key, response);
        return response;
    }

    /**
     * Tags are case-sensitive
     */
    private Tagging createTags(final Map<String, String> tags) {
        // Tag keys are case-sensitive, meta is not
        if (NullSafe.hasEntries(tags)) {
            final Set<Tag> tagSet = tags.entrySet()
                    .stream()
                    .map(entry ->
                            Tag.builder()
                                    .key(entry.getKey())
                                    .value(entry.getValue())
                                    .build())
                    .collect(Collectors.toSet());

            return Tagging.builder()
                    .tagSet(tagSet)
                    .build();
        } else {
            return Tagging.builder()
                    .build();
        }
    }

    private PutObjectRequest createPutObjectRequest(final String bucketName,
                                                    final String key,
                                                    final Map<String, String> tags,
                                                    final Map<CIKey, String> s3Metadata,
                                                    final Path source) {
        Objects.requireNonNull(source);
        final Builder builder = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .tagging(createTags(tags))
                .metadata(convertS3MetadataMap(s3Metadata));

        if (isZStandardFile(source)) {
            builder.contentEncoding(ZSTD_CONTENT_ENCODING);
        }
        return builder.build();
    }

    private boolean isZStandardFile(final Path file) {
        if (file != null) {
            final String lowerFilename = file.getFileName().toString().toLowerCase();
            return lowerFilename.endsWith(".zst") || lowerFilename.endsWith(".zstd");
        } else {
            return false;
        }

    }

    private Map<String, String> convertS3MetadataMap(final Map<CIKey, String> s3Metadata) {
        final Map<String, String> map = CIKey.convertToLowerCaseStringMap(s3Metadata);
        map.keySet()
                .forEach(S3Util::validateMetadataKey);
        return map;
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

    private void debug(final String message,
                       final String bucketName,
                       final String key,
                       final Path path,
                       final Exception e) {
        LOGGER.debug(() -> LogUtil.message("{} {}, path: {}, message: {}",
                message,
                getDebugIdentity(bucketName, key),
                NullSafe.get(path, Path::toAbsolutePath),
                LogUtil.exceptionMessage(e)), e);
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
                       final Path path,
                       final Exception e) {
        LOGGER.error(() -> LogUtil.message("{} {}, path: {}, message: {}",
                message,
                getDebugIdentity(bucketName, key),
                NullSafe.get(path, Path::toAbsolutePath),
                LogUtil.exceptionMessage(e)), e);
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


    /**
     * Basic information about an S3 object.
     *
     * @param bucketName    S3 bucket name.
     * @param key           S3 object key.
     * @param contentLength Size of the object in bytes.
     * @param tagCount      The number of tags on the object.
     * @param s3Metadata    The Metadata for the S3 object.
     *                      This could be used for Stroom manifest data, Stroom meta or anything else.
     *                      Caller is resposible for namespacing entries if different types are used.
     */
    public record S3ObjectInfo(
            String bucketName,
            String key,
            long contentLength,
            int tagCount,
            Map<CIKey, String> s3Metadata) {

    }
}
