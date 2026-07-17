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

import stroom.aws.s3.client.S3ClientHelper;
import stroom.aws.s3.client.S3ClientHelper.S3ObjectInfo;
import stroom.aws.s3.client.S3UploadProperties;
import stroom.aws.s3.client.S3Util;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.aws.s3.shared.S3Location;
import stroom.cache.api.TemplateCache;
import stroom.meta.api.AttributeMap;
import stroom.meta.shared.Meta;
import stroom.meta.shared.SimpleMeta;
import stroom.util.collections.CollectionUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Range;
import stroom.util.shared.string.CIKey;
import stroom.util.string.StringIdUtil;
import stroom.util.string.TemplateUtil.ContextVariableResolver;
import stroom.util.string.TemplateUtil.ExecutorBuilder;
import stroom.util.string.TemplateUtil.Template;
import stroom.util.time.TimeBasis;

import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final CIKey SEQUENCE_NO_VAR = CIKey.internStaticKey("sequenceNo");
    private static final String SEPARATE_META_FILE_METADATA_KEY = "has-stroom-meta-file";

    static final String META_METADATA_KEY_PREFIX = "meta-";

    public static final String META_ID_TAG_KEY = "meta-id";

    private final S3ClientConfig s3ClientConfig;
    private final S3ClientHelper s3ClientHelper;
    private final TemplateCache templateCache;
    private final ContextVariableResolver contextVariableResolver;

    public S3Manager(final S3ClientConfig s3ClientConfig,
                     final S3ClientHelper s3ClientHelper,
                     final TemplateCache templateCache,
                     final ContextVariableResolver contextVariableResolver) {
        this.s3ClientConfig = s3ClientConfig;
        this.s3ClientHelper = s3ClientHelper;
        this.templateCache = templateCache;
        this.contextVariableResolver = contextVariableResolver;
    }

    /**
     * Create an S3 bucket name the bucketName from the s3ClientConfig.
     * {@link Meta} is used to provide values for the templated bucket name.
     */
    public String createBucketName(final SimpleMeta meta, final TimeBasis timeBasis) {
        return createBucketName(null, timeBasis);
    }

    /**
     * Create an S3 bucket name using either the supplied bucketNamePattern or the bucketName from
     * the s3ClientConfig. {@link Meta} is used to provide values for the templated bucket name.
     *
     * @param bucketNamePattern If null, it will use the bucketName from the s3ClientConfig.
     */
    public String createBucketName(final String bucketNamePattern,
                                   final SimpleMeta meta,
                                   final TimeBasis timeBasis) {
        Objects.requireNonNull(meta);
        final String effectiveBucketNamePattern = NullSafe.nonBlankStringElseGet(
                bucketNamePattern,
                this::getBucketNamePattern);

        String bucketName = applyTemplate(effectiveBucketNamePattern, meta, timeBasis);
        bucketName = S3Util.cleanBucketName(bucketName);
        final int len = bucketName.length();
        if (len < 3) {
            LOGGER.error("Bucket name too short, must be >=3. bucketName: '{}'", bucketName);
            throw new RuntimeException(LogUtil.message("Bucket name too short, must be >=3. bucketName: '{}'",
                    bucketName));
        } else if (len > 63) {
            LOGGER.warn("Truncating bucket name: '{}'. Length must be >=3 and <=63.", bucketName);
            return bucketName.substring(0, 63);
        }
        LOGGER.debug("createBucketName() - bucketNamePattern: '{}', meta: '{}', bucketName: '{}'",
                bucketNamePattern, meta, bucketName);
        return bucketName;
    }

    /**
     * Create an S3 key using the keyPattern from the s3ClientConfig.
     * {@link Meta} is used to provide values for the templated key.
     */
    public String createKey(final SimpleMeta meta, final TimeBasis timeBasis) {
        return createKey(null, meta, null, timeBasis);
    }

    /**
     * Create an S3 key using either the supplied keyPattern or the keyPattern from
     * the s3ClientConfig. {@link Meta} is used to provide values for the templated key.
     *
     * @param keyPattern If null, it will use the keyPattern from the s3ClientConfig.
     */
    public String createKey(final String keyPattern, final SimpleMeta meta, final TimeBasis timeBasis) {
        return createKey(keyPattern, meta, null, timeBasis);
    }

    /**
     * Derive an S3Location based on the configured region and bucket/key templates in
     * combination with {@link SimpleMeta}.
     */
    public S3Location deriveS3Location(final SimpleMeta meta, final TimeBasis timeBasis) {
        final String region = s3ClientConfig.getRegion();
        final String key = createKey(meta, timeBasis);
        final String bucket = createBucketName(meta, timeBasis);
        final S3Location s3Location = new S3Location(region, key, bucket);
        LOGGER.debug("createS3Location() - meta: {}, s3Location: {}", meta, s3Location);
        return s3Location;
    }

    /**
     * Create an S3 key using either the supplied keyPattern or the keyPattern from
     * the s3ClientConfig. {@link Meta} is used to provide values for the templated key.
     *
     * @param keyPattern If null, it will use the keyPattern from the s3ClientConfig.
     */
    public String createKey(final String keyPattern,
                            final SimpleMeta meta,
                            final AtomicLong sequenceNumber,
                            final TimeBasis timeBasis) {
        Objects.requireNonNull(meta);
        final String effectiveKeyPattern = NullSafe.nonBlankStringElseGet(
                keyPattern,
                this::getKeyNamePattern);

        String key = applyTemplate(effectiveKeyPattern, meta, sequenceNumber, timeBasis);
        key = S3Util.cleanKeyName(key);

        final int keyBytesLen = key.getBytes(StandardCharsets.UTF_8).length;
        if (keyBytesLen > 1024) {
            throw new RuntimeException(LogUtil.message("Key name '{}' too long {}, must be less than 1,024 bytes",
                    key, keyBytesLen));
        }
        LOGGER.debug("createKey() - keyPattern: '{}', meta: '{}', key: '{}'",
                keyPattern, meta, key);
        return key;
    }

    /**
     * @return The bucket name or pattern from client config, or a default if not set.
     */
    public String getBucketNamePattern() {
        return NullSafe.nonBlankStringElse(s3ClientConfig.getBucketName(),
                S3ClientConfig.DEFAULT_BUCKET_NAME);
    }

    /**
     * @return The key or pattern from client config, or a default if not set.
     */
    public String getKeyNamePattern() {
        return NullSafe.nonBlankStringElse(
                s3ClientConfig.getKeyPattern(),
                S3ClientConfig.DEFAULT_KEY_PATTERN);
    }

    /**
     * @return The region from client config
     */
    public String getRegion() {
        return s3ClientConfig.getRegion();
    }

    /**
     * @return The first non-blank pattern out of:
     * <ol>
     *     <li>keyNamePattern</li>
     *     <li>s3ClientConfig.getKeyPattern</li>
     *     <li>s3ClientConfig.DEFAULT_KEY_PATTERN</li>
     * </ol>
     */
    public String getKeyNamePattern(final String keyNamePattern) {
        if (NullSafe.isNonBlankString(keyNamePattern)) {
            return keyNamePattern;
        } else {
            return NullSafe
                    .nonBlankStringElse(
                            s3ClientConfig.getKeyPattern(),
                            S3ClientConfig.DEFAULT_KEY_PATTERN);
        }
    }

    /**
     * @return The first non-blank pattern out of:
     * <ol>
     *     <li>bucketNamePattern</li>
     *     <li>s3ClientConfig.getBucketName</li>
     *     <li>s3ClientConfig.DEFAULT_BUCKET_NAME</li>
     * </ol>
     */
    public String getBucketNamePattern(final String bucketNamePattern) {
        if (NullSafe.isNonBlankString(bucketNamePattern)) {
            return bucketNamePattern;
        } else {
            return NullSafe.nonBlankStringElse(
                    s3ClientConfig.getBucketName(),
                    S3ClientConfig.DEFAULT_BUCKET_NAME);
        }
    }

    public PutObjectResponse upload(final Meta meta,
                                    final AttributeMap attributeMap,
                                    final Path source,
                                    final TimeBasis timeBasis) {
        return upload(
                getBucketNamePattern(),
                getKeyNamePattern(),
                meta,
                convertAttributeMapToS3Metadata(attributeMap),
                null,
                source,
                timeBasis);
    }

    public PutObjectResponse upload(final Meta meta,
                                    final Map<CIKey, String> s3MetaData,
                                    final Path source,
                                    final TimeBasis timeBasis) {
        return upload(
                getBucketNamePattern(),
                getKeyNamePattern(),
                meta,
                s3MetaData,
                null,
                source,
                timeBasis);
    }

    /**
     * Upload method for fixed bucket/key, not templates.
     *
     * @param bucketName The S3 bucket to upload to (Use
     *                   {@link S3Manager#createBucketName(String, SimpleMeta, TimeBasis)}
     *                   to create a bucket from a pattern).
     * @param key        The S3 key to upload to (Use
     *                   {@link S3Manager#createKey(String, SimpleMeta, AtomicLong, TimeBasis)}
     *                   to create a key from a pattern).
     */
    public PutObjectResponse upload(final String bucketName,
                                    final String key,
                                    final Meta meta,
                                    final AttributeMap attributeMap,
                                    final S3UploadProperties uploadProperties,
                                    final Path source) {
        NullSafe.requireNonBlankString(bucketName);
        NullSafe.requireNonBlankString(key);
        final Map<String, String> tags = createS3TagsFromMeta(meta);
        return s3ClientHelper.upload(
                bucketName,
                key,
                tags,
                convertAttributeMapToS3Metadata(attributeMap),
                uploadProperties,
                source);
    }

    /**
     * Upload method for fixed bucket/key, not templates.
     *
     * @param bucketName The S3 bucket to upload to (Use
     *                   {@link S3Manager#createBucketName(String, SimpleMeta, TimeBasis)}
     *                   to create a bucket from a pattern).
     * @param key        The S3 key to upload to (Use
     *                   {@link S3Manager#createKey(String, SimpleMeta, AtomicLong, TimeBasis)}
     *                   to create a key from a pattern).
     */
    public PutObjectResponse upload(final String bucketName,
                                    final String key,
                                    final Meta meta,
                                    final Map<CIKey, String> s3MetaData,
                                    final S3UploadProperties uploadProperties,
                                    final Path source) {
        NullSafe.requireNonBlankString(bucketName);
        NullSafe.requireNonBlankString(key);
        final Map<String, String> tags = createS3TagsFromMeta(meta);
        return s3ClientHelper.upload(
                bucketName,
                key,
                tags,
                s3MetaData,
                uploadProperties,
                source);
    }

    public PutObjectResponse upload(final String bucketNamePattern,
                                    final String keyNamePattern,
                                    final Meta meta,
                                    final Map<CIKey, String> s3MetaData,
                                    final S3UploadProperties uploadProperties,
                                    final Path source,
                                    final TimeBasis timeBasis) {
        final String bucketName = createBucketName(bucketNamePattern, meta, timeBasis);
        final String key = createKey(keyNamePattern, meta, timeBasis);
        final Map<String, String> tags = createS3TagsFromMeta(meta);
        return s3ClientHelper.upload(
                bucketName,
                key,
                tags,
                s3MetaData,
                uploadProperties,
                source);
    }

    private Map<CIKey, String> convertAttributeMapToS3Metadata(final AttributeMap attributeMap) {
        if (NullSafe.isEmptyMap(attributeMap)) {
            return Collections.emptyMap();
        } else {
            return CollectionUtil.mappingKeys(attributeMap, key -> {
                final String cleanedKey = S3Util.cleanS3MetaDataKey(key);
                if (LOGGER.isDebugEnabled()) {
                    if (Objects.equals(key, cleanedKey)) {
                        LOGGER.debug("convertAttributeMapToS3Metadata() - key '{}' cleaned to '{}'",
                                key, cleanedKey);
                    }
                }
                return CIKey.of(cleanedKey);
            });
        }
    }

//    private PutObjectResponse tryUpload(final String bucketName,
//                                        final String key,
//                                        final Meta meta,
//                                        final AttributeMap attributeMap,
//                                        final Path source) {
//        final PutObjectRequest request = createPutObjectRequest(bucketName, key, meta, attributeMap, source);
//        logRequest("Uploading: ", bucketName, key, request);
//
//        final PutObjectResponse response;
//        if (s3ClientConfig.isAsync()) {
//            response = s3ClientPool.getWithAsyncS3Client(s3ClientConfig, s3AsyncClient -> {
//                if (s3ClientConfig.isMultipart()) {
//                    try (final S3TransferManager transferManager =
//                            S3TransferManager.builder()
//                                    .s3Client(s3AsyncClient)
//                                    .build()) {
//
//                        final UploadFileRequest uploadFileRequest =
//                                UploadFileRequest.builder()
//                                        .putObjectRequest(request)
//                                        .addTransferListener(LoggingTransferListener.create())
//                                        .source(source)
//                                        .build();
//
//                        final FileUpload fileUpload = transferManager.uploadFile(uploadFileRequest);
//
//                        final CompletedFileUpload uploadResult = fileUpload.completionFuture().join();
//                        LOGGER.debug(() -> "Upload result: " +
//                                           getDebugIdentity(bucketName, key) +
//                                           ", result=" +
//                                           uploadResult);
//                        return uploadResult.response();
//                    } catch (final RuntimeException e) {
//                        debug("Error putting object (async, multi-part)", bucketName, key, source, e);
//                        throw e;
//                    }
//                } else {
//                    try {
//                        return s3AsyncClient.putObject(request, source).join();
//                    } catch (final Exception e) {
//                        debug("Error putting object (async)", bucketName, key, source, e);
//                        throw e;
//                    }
//                }
//            });
//        } else {
//            response = s3ClientPool.getWithS3Client(s3ClientConfig, s3Client -> {
//                try {
//                    LOGGER.debug(() -> LogUtil.message(
//                            "tryUpload() - Putting Object (sync) - bucketName: {}, key: {}, source: {}, meta: {}, " +
//                            "requestMeta: {}, tags: {}",
//                            bucketName,
//                            key,
//                            source.toAbsolutePath(),
//                            meta,
//                            request.metadata(),
//                            request.tagging()));
//                    return s3Client.putObject(request, source);
//                } catch (final Exception e) {
//                    debug("Error putting object (sync)", bucketName, key, source, e);
//                    throw e;
//                }
//            });
//        }
//
//        logResponse("Uploaded: ", bucketName, key, response);
//        return response;
//    }

//    private void createBucket(final String bucketName) {
//        final CreateBucketRequest request = CreateBucketRequest.builder()
//                .bucket(bucketName)
//                .build();
//        logRequest("Creating bucket: ", bucketName, null, request);
//
//        final CreateBucketResponse response;
//        if (s3ClientConfig.isAsync()) {
//            try (final PooledClient<S3AsyncClient> pooledClient = getAsyncClient()) {
//                response = pooledClient.getClient()
//                        .createBucket(request)
//                        .join();
//            } catch (final S3Exception e) {
//                error("Error creating bucket: ", bucketName, null, e);
//                throw e;
//            }
//        } else {
//            try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
//                response = pooledClient.getClient().createBucket(request);
//            } catch (final S3Exception e) {
//                error("Error creating bucket: ", bucketName, null, e);
//                throw e;
//            }
//        }
//
//        logResponse("Created bucket: ", bucketName, null, response);
//    }

    /**
     * Get part of an S3 object, defined by a contiguous byte range.
     *
     * @param meta      The {@link Meta} the object belongs to.
     * @param byteRange The range of bytes to fetch.
     * @return The repose containing the byte range.
     */
    public ResponseInputStream<GetObjectResponse> getObject(final Meta meta,
                                                            final Range<Long> byteRange,
                                                            final TimeBasis timeBasis) {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(byteRange);
        final String bucketName = createBucketName(getBucketNamePattern(), meta, timeBasis);
        final String key = createKey(getKeyNamePattern(), meta, timeBasis);
        return s3ClientHelper.getObjectByteRange(bucketName, key, byteRange);
    }

    public ResponseInputStream<GetObjectResponse> getObject(final String bucketName,
                                                            final String key) {
        NullSafe.requireNonBlankString(bucketName);
        NullSafe.requireNonBlankString(key);
        return s3ClientHelper.getObject(bucketName, key);
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
                                                               final Range<Long> byteRange,
                                                               final TimeBasis timeBasis) {
        return getByteRange(meta, childStreamType, getKeyNamePattern(), byteRange, timeBasis);
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
                                                               final String keyNamePattern,
                                                               final Range<Long> byteRange,
                                                               final TimeBasis timeBasis) {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(byteRange);
        final String bucketName = createBucketName(getBucketNamePattern(), meta, timeBasis);
        final String key = createKey(keyNamePattern, meta, timeBasis);
        return s3ClientHelper.getObjectByteRange(bucketName, key, byteRange);
    }

    public long getFileSize(final Meta meta,
                            final String childStreamType,
                            final TimeBasis timeBasis) {
        return getFileSize(meta, childStreamType, getKeyNamePattern(), timeBasis);
    }

    public long getFileSize(final Meta meta,
                            final String childStreamType,
                            final String keyNamePattern,
                            final TimeBasis timeBasis) {
        Objects.requireNonNull(meta);
        final String bucketName = createBucketName(getBucketNamePattern(), meta, timeBasis);
        final String key = createKey(keyNamePattern, meta, timeBasis);
        return s3ClientHelper.getFileSize(bucketName, key);
    }

    public S3ObjectInfo getObjectInfo(final Meta meta,
                                      final String keyNamePattern,
                                      final TimeBasis timeBasis) {
        Objects.requireNonNull(meta);
        final String bucketName = createBucketName(getBucketNamePattern(), meta, timeBasis);
        final String key = createKey(keyNamePattern, meta, timeBasis);
        return s3ClientHelper.getObjectInfo(bucketName, key);

//        final HeadObjectRequest request = HeadObjectRequest.builder()
//                .bucket(bucketName)
//                .key(key)
//                .build();
//
//        logRequest("HEAD: ", bucketName, key, request);
//
//        try (final PooledClient<S3Client> pooledClient = getSyncClient()) {
//            final HeadObjectResponse headObjectResponse = LOGGER.logDurationIfDebugEnabled(
//                    () -> pooledClient.getClient().headObject(request),
//                    () -> LogUtil.message("getObjectInfo() - bucket: '{}', key: '{}'",
//                            bucketName, key));
//
//            final Map<String, String> metadata = headObjectResponse.metadata();
//            final long contentLength = Objects.requireNonNullElse(
//                    headObjectResponse.contentLength(),
//                    0L);
//
//            final AttributeMap manifest;
//            final List<AttributeMap> attributeMaps;
//            if (NullSafe.hasEntries(metadata)) {
//                manifest = readManifest(metadata);
//                attributeMaps = readMeta(metadata);
//            } else {
//                manifest = new AttributeMap();
//                attributeMaps = Collections.emptyList();
//            }
//
//            return new S3ObjectInfo(
//                    bucketName,
//                    key,
//                    contentLength,
//                    attributeMaps,
//                    manifest,
//                    false);
//        } catch (final NoSuchKeyException e) {
//            error("Error getting object info: ", bucketName, key, e);
//            throw new RuntimeException(LogUtil.message("No data found for meta: {} using key: {}, bucket: {}",
//                    meta, key, bucketName), e);
//        } catch (final RuntimeException e) {
//            error("Error getting object info: ", bucketName, key, e);
//            throw e;
//        }
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
        key = S3Util.removeAwsPrefix(key);

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
                                      final Path dest,
                                      final TimeBasis timeBasis) {
        return download(meta,
                null,
                null,
                getKeyNamePattern(),
                dest,
                true,
                timeBasis);
    }

    public GetObjectResponse download(final Meta meta,
                                      final String childStreamType,
                                      final String bucketName,
                                      final String key,
                                      final Path dest,
                                      final boolean allowAsync) {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(dest);
        NullSafe.requireNonBlankString(bucketName);
        NullSafe.requireNonBlankString(key);
        return s3ClientHelper.download(bucketName, key, dest, allowAsync);
    }

    public GetObjectResponse download(final Meta meta,
                                      final String childStreamType,
                                      final String bucketNamePattern,
                                      final String keyNamePattern,
                                      final Path dest,
                                      final boolean allowAsync,
                                      final TimeBasis timeBasis) {
        Objects.requireNonNull(meta);
        Objects.requireNonNull(dest);
        final String bucketName = createBucketName(bucketNamePattern, meta, timeBasis);
        final String key = createKey(keyNamePattern, meta, timeBasis);

        return s3ClientHelper.download(bucketName, key, dest, allowAsync);
    }

    public List<String> listKeys(final Meta meta,
                                 final String childStreamType,
                                 final String keyNamePattern,
                                 final TimeBasis timeBasis) {

        final String bucketName = createBucketName(getBucketNamePattern(), meta, timeBasis);
        final String key = createKey(keyNamePattern, meta, timeBasis);
        return s3ClientHelper.listKeys(bucketName, key);
    }

    public DeleteObjectResponse delete(final Meta meta,
                                       final TimeBasis timeBasis) {
        return delete(meta, getBucketNamePattern(), getKeyNamePattern(), timeBasis);
    }

    public DeleteObjectResponse delete(final Meta meta,
                                       final String bucketNamePattern,
                                       final String keyNamePattern,
                                       final TimeBasis timeBasis) {
        final String bucketName = createBucketName(bucketNamePattern, meta, timeBasis);
        final String key = createKey(keyNamePattern, meta, timeBasis);
        return s3ClientHelper.delete(bucketName, key);
    }

    private Map<String, String> createS3TagsFromMeta(final Meta meta) {
        Objects.requireNonNull(meta);
        return Map.of(
                S3ClientHelper.FEED_TAG_KEY, meta.getFeedName(),
                S3ClientHelper.STREAM_TYPE_TAG_KEY, meta.getTypeName(),
                META_ID_TAG_KEY, Long.toString(meta.getId()));
    }

    private String applyTemplate(final String templateStr,
                                 final SimpleMeta meta,
                                 final TimeBasis timeBasis) {
        return applyTemplate(templateStr, meta, null, timeBasis);
    }

    private String applyTemplate(final String templateStr,
                                 final SimpleMeta meta,
                                 final AtomicLong sequenceNumber,
                                 final TimeBasis timeBasis) {
        final Template template = templateCache.getTemplate(templateStr);
        ExecutorBuilder executorBuilder = template.buildExecutor();

        executorBuilder = executorBuilder
                .addUuidReplacement(false)
                .addLazyReplacement(FEED_VAR, meta::getFeedName)
                .addLazyReplacement(TYPE_VAR, meta::getTypeName)
                .addLazyReplacement(ID_VAR, () -> String.valueOf(meta.getId()))
                .addLazyReplacement(ID_PATH_VAR, () -> getIdPath(meta.getId()))
                .addLazyReplacement(ID_PADDED_VAR, () -> padId(meta.getId()))
                .addStandardTimeReplacements(() ->
                        getTimeForVariableReplacement(timeBasis, meta));

        if (contextVariableResolver != null) {
            contextVariableResolver.addContextReplacements(executorBuilder);
        }

        if (sequenceNumber != null) {
            executorBuilder = executorBuilder.addSequenceNumberReplacement(
                    SEQUENCE_NO_VAR, sequenceNumber, true);
        }
        final String output = executorBuilder.execute();

        LOGGER.debug("applyTemplate() - template: '{}', timeBasis: {}, output: '{}", template, timeBasis, output);
        return output;
    }

    @NonNull
    private ZonedDateTime getTimeForVariableReplacement(final TimeBasis timeBasis,
                                                        final SimpleMeta meta) {
        final ZonedDateTime time = switch (timeBasis) {
            case CURRENT_TIME -> ZonedDateTime.now();
            case META_CREATION_TIME -> ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(Objects.requireNonNull(meta).getCreateMs()),
                    ZoneOffset.UTC);
            case null -> ZonedDateTime.now();
        };

        LOGGER.debug("getTimeForVariableReplacement() - timeBasis: {}, meta: {}, time: {}",
                timeBasis, meta, time);
        return time;
    }

//    /**
//     * Creates a string representation of an S3 object of the form
//     * <pre>
//     * s3://region/bucket/key
//     * </pre>
//     */
//    public String toS3PathString(final Meta meta, final String keyNamePattern) {
//        final String bucketName = createBucketName(getBucketNamePattern(), meta);
//        final String keyName = createKey(
//                Objects.requireNonNullElseGet(keyNamePattern, this::getKeyNamePattern),
//                meta);
//        return String.join(
//                "/",
//                "s3:/",
//                s3ClientConfig.getRegion(),
//                bucketName,
//                keyName);
//    }

//    public DeleteObjectResponse delete(final Meta meta) {
//        final String bucketName = createBucketName(getBucketNamePattern(), meta);
//        final String key = createKey(getKeyNamePattern(), meta);
//        final DeleteObjectRequest request = DeleteObjectRequest.builder()
//                .bucket(bucketName)
//                .key(key)
//                .build();
//        logRequest("Deleting: ", bucketName, key, request);
//
//        final DeleteObjectResponse response;
//        if (s3ClientConfig.isAsync()) {
//            try (final S3AsyncClient s3AsyncClient = createAsyncClient(s3ClientConfig)) {
//                response = s3AsyncClient.deleteObject(request).join();
//            } catch (final S3Exception e) {
//                error("Error deleting: ", bucketName, key, e);
//                throw e;
//            }
//        } else {
//            try (final S3Client s3Client = createClient(s3ClientConfig)) {
//                response = s3Client.deleteObject(request);
//            } catch (final S3Exception e) {
//                error("Error deleting: ", bucketName, key, e);
//                throw e;
//            }
//        }
//
//        logResponse("Deleted: ", bucketName, key, response);
//        return response;
//    }
//
//    private Tagging createTags(final Meta meta) {
//        return Tagging.builder()
//                .tagSet(
//                        Tag.builder().key("feed").value(meta.getFeedName()).build(),
//                        Tag.builder().key("stream-type").value(meta.getTypeName()).build(),
//                        Tag.builder().key("meta-id").value(String.valueOf(meta.getId())).build()
//                )
//                .build();
//    }
//
//    public String createKey(final String keyPattern, final Meta meta) {
//        String keyName = keyPattern;
//        final ZonedDateTime zonedDateTime =
//                ZonedDateTime.ofInstant(Instant.ofEpochMilli(meta.getCreateMs()), ZoneOffset.UTC);
//        final String idPadded = padId(meta.getId());
//        keyName = pathCreator.replaceTimeVars(keyName, zonedDateTime);
//        // Parse for stuff like partNo, pipeline, node, etc.
//        keyName = pathCreator.replace(keyName, "feed", meta::getFeedName);
//        keyName = pathCreator.replace(keyName, "type", meta::getTypeName);
//        keyName = pathCreator.replace(keyName, "id", () -> String.valueOf(meta.getId()));
//        keyName = pathCreator.replace(keyName, "idPath", () -> getIdPath(idPadded));
//        keyName = pathCreator.replace(keyName, "idPadded", () -> idPadded);
//        keyName = pathCreator.replaceContextVars(keyName);
//        keyName = pathCreator.replaceUUIDVars(keyName);
//
//        keyName = S3_KEY_NAME_PATTERN.matcher(keyName).replaceAll("-");
//        keyName = MULTI_SLASH.matcher(keyName).replaceAll("/");
//        keyName = LEADING_SLASH.matcher(keyName).replaceAll("");
//        keyName = TRAILING_SLASH.matcher(keyName).replaceAll("");
//
//        if (keyName.getBytes(StandardCharsets.UTF_8).length > 1024) {
//            throw new RuntimeException("Key name too long: " + keyName);
//        }
//
//        return keyName;
//    }
//

//    /**
//     * Creates a string representation of an S3 object of the form
//     * <pre>
//     * s3://region/bucket/key
//     * </pre>
//     */
//    public String toS3PathString(final Meta meta, final String keyNamePattern) {
//        final String bucketName = createBucketName(getBucketNamePattern(), meta);
//        final String key = createKey(getKeyNamePattern(), meta);
//        final DeleteObjectRequest request = DeleteObjectRequest.builder()
//                .bucket(bucketName)
//                .key(key)
//                .build();
//        logRequest("Deleting: ", bucketName, key, request);
//
//        final DeleteObjectResponse response;
//        if (s3ClientConfig.isAsync()) {
//            try (final S3AsyncClient s3AsyncClient = createAsyncClient(s3ClientConfig)) {
//                response = s3AsyncClient.deleteObject(request).join();
//            } catch (final S3Exception e) {
//                error("Error deleting: ", bucketName, key, e);
//                throw e;
//            }
//        } else {
//            try (final S3Client s3Client = createClient(s3ClientConfig)) {
//                response = s3Client.deleteObject(request);
//            } catch (final S3Exception e) {
//                error("Error deleting: ", bucketName, key, e);
//                throw e;
//            }
//        }
//
//        logResponse("Deleted: ", bucketName, key, response);
//        return response;
//    }
//
//    private Tagging createTags(final Meta meta) {
//        return Tagging.builder()
//                .tagSet(
//                        Tag.builder().key("feed").value(meta.getFeedName()).build(),
//                        Tag.builder().key("stream-type").value(meta.getTypeName()).build(),
//                        Tag.builder().key("meta-id").value(String.valueOf(meta.getId())).build()
//                )
//                .build();
//    }

//    public String createKey(final String keyPattern, final Meta meta) {
//        String keyName = keyPattern;
//        final ZonedDateTime zonedDateTime =
//                ZonedDateTime.ofInstant(Instant.ofEpochMilli(meta.getCreateMs()), ZoneOffset.UTC);
//        final String idPadded = padId(meta.getId());
//        keyName = pathCreator.replaceTimeVars(keyName, zonedDateTime);
//        keyName = pathCreator.replace(keyName, "feed", meta::getFeedName);
//        keyName = pathCreator.replace(keyName, "type", meta::getTypeName);
//        keyName = pathCreator.replace(keyName, "id", () -> String.valueOf(meta.getId()));
//        keyName = pathCreator.replace(keyName, "idPath", () -> getIdPath(idPadded));
//        keyName = pathCreator.replace(keyName, "idPadded", () -> idPadded);
//
//        keyName = S3_KEY_NAME_PATTERN.matcher(keyName).replaceAll("-");
//        keyName = MULTI_SLASH.matcher(keyName).replaceAll("/");
//        keyName = LEADING_SLASH.matcher(keyName).replaceAll("");
//        keyName = TRAILING_SLASH.matcher(keyName).replaceAll("");
//
//        if (keyName.getBytes(StandardCharsets.UTF_8).length > 1024) {
//            throw new RuntimeException("Key name too long: " + keyName);
//        }
//
//        return keyName;
//    }

    /**
     * Pad a prefix.
     */
    private static String padId(final long current) {
        return StringIdUtil.idToString(current);
    }

    /**
     * Pkg private for testing
     *
     * @return The metaId as a directory path with one dir for 1000 metaIds,
     * e.g. metaId 123,456,789 => "123/456"
     */
    static String getIdPath(final long metaId) {
        final String idStr = padId(metaId);
        final StringBuilder sb = new StringBuilder();
        final int endIdxExc = idStr.length() - PAD_SIZE;
        for (int i = 0; i < endIdxExc; i += PAD_SIZE) {
            final String part = idStr.substring(i, i + PAD_SIZE);
            if (!sb.isEmpty()) {
                sb.append("/");
            }
            sb.append(part);
        }
        return sb.toString();
    }

//    private PutObjectRequest createPutObjectRequest(final String bucketName,
//                                                    final String key,
//                                                    final Meta meta,
//                                                    final AttributeMap attributeMap,
//                                                    final S3UploadProperties uploadProperties) {
//        final Map<String, String> metadata = attributeMap
//                .entrySet()
//                .stream()
//                .collect(Collectors.toMap(e -> createS3Name(e.getKey()), Entry::getValue));
//
//        final PutObjectRequest.Builder builder = PutObjectRequest.builder()
//                .bucket(bucketName)
//                .key(key)
//                .tagging(createTags(meta))
//                .metadata(metadata);
//
//        if (uploadProperties != null) {
//            NullSafe.consumeNonBlankString(uploadProperties.cacheControl(), builder::cacheControl);
//            NullSafe.consumeNonBlankString(uploadProperties.contentDisposition(), builder::contentDisposition);
//            NullSafe.consumeNonBlankString(uploadProperties.contentEncoding(), builder::contentEncoding);
//            NullSafe.consumeNonBlankString(uploadProperties.contentType(), builder::contentType);
//                    }
//                    return ciKey;
//                });
//    }

    /**
     * Remove a prefix if present.
     *
     * @param key
     * @param lowerCasePrefix The prefix in lowercase
     * @return
     */
    public CIKey removePrefix(final CIKey key, final String lowerCasePrefix) {
        if (NullSafe.isEmptyString(lowerCasePrefix)) {
            return key;
        } else {
            return NullSafe.get(
                    key,
                    ciKey -> {
                        if (ciKey.startsWithLowerCase(lowerCasePrefix)) {
                            ciKey = ciKey.substring(lowerCasePrefix.length());
                        }
                        return ciKey;
                    });
        }
    }

//    private PutObjectRequest createPutObjectRequest(final String bucketName,
//                                                    final String key,
//                                                    final Meta meta,
//                                                    final AttributeMap attributeMap,
//                                                    final Path source) {
//
//        // Convert the manifest attributeMap into s3 metadata key/value pairs to save
//        // creating a tiny file for them.
//        final Map<String, String> metadata = NullSafe.map(attributeMap)
//                .entrySet()
//                .stream()
//                .filter(entry -> NullSafe.isNonBlankString(entry.getKey()))
//                .map(entry -> Map.entry(
//                        createManifestKey(entry.getKey()),
//                        entry.getValue()))
//                .filter(entry -> entry.getKey() != null)
//                .collect(Collectors.toMap(
//                        Entry::getKey,
//                        Entry::getValue));
//
//        final Builder builder = PutObjectRequest.builder()
//                .bucket(bucketName)
//                .key(key)
//                .tagging(createS3TagsFromMeta(meta))
//                .metadata(metadata);
//        if (source.getFileName().toString().endsWith(".zst")) {
//            builder.contentEncoding("zstd");
//        }
//        return builder.build();
//    }


    // --------------------------------------------------------------------------------


//    public record S3ObjectInfo(
//            String bucketName,
//            String key,
//            long contentLength,
//            List<AttributeMap> meta,
//            AttributeMap manifest,
//            boolean hasMetaFile) {
//
//    }


    // --------------------------------------------------------------------------------


    /**
     * @param segmentIdx The part/segment index that the meta entry is for
     * @param key        Meta entry key
     * @param value      Meta entry value
     */
    record SegmentedMetaEntry(int segmentIdx, String key, String value) {

    }
}
