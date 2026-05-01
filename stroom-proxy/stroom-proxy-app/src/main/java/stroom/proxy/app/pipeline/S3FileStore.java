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

package stroom.proxy.app.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * AWS S3 (or S3-compatible) implementation of {@link FileStore}.
 * <p>
 * Producers write to a local staging directory. Calling
 * {@link FileStoreWrite#commit()} uploads all staged files to S3 and
 * returns a {@link FileStoreLocation} with an {@code s3://} URI.
 * </p>
 * <p>
 * Consumers call {@link #resolve(FileStoreLocation)} which downloads
 * the file group from S3 to a local cache directory and returns the
 * local path. This local cache is throwaway — it is cleaned up when
 * {@link #delete(FileStoreLocation)} is called.
 * </p>
 * <p>
 * File-group layout in S3:
 * <pre>
 *   s3://bucket/keyPrefix/writerId/seqId/
 *       proxy.meta
 *       proxy.zip
 *       proxy.entries
 *       .committed          ← commit marker
 * </pre>
 * </p>
 */
public class S3FileStore implements FileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3FileStore.class);

    private static final String COMMITTED_MARKER = ".committed";
    private static final String CACHE_DIR_NAME = ".cache";
    private static final String STAGING_DIR_NAME = ".staging";
    private static final int ID_WIDTH = 10;

    private final String name;
    private final String bucket;
    private final String keyPrefix;
    private final S3Client s3Client;
    private final Path localStagingRoot;
    private final Path localCacheRoot;
    private final String writerId;
    private final AtomicLong sequence = new AtomicLong();

    /**
     * Create an S3FileStore from a {@link FileStoreDefinition}.
     *
     * @param name       The logical store name.
     * @param definition The file store definition with S3 config.
     * @param localRoot  The local root directory for staging and cache.
     */
    public S3FileStore(final String name,
                       final FileStoreDefinition definition,
                       final Path localRoot) {
        this(
                name,
                requireNonBlank(definition.getBucket(), "bucket"),
                definition.getEffectiveKeyPrefix(name),
                buildS3Client(definition),
                localRoot,
                UUID.randomUUID().toString());
    }

    /**
     * Test-friendly constructor with explicit S3 client.
     */
    S3FileStore(final String name,
                final String bucket,
                final String keyPrefix,
                final S3Client s3Client,
                final Path localRoot,
                final String writerId) {
        this.name = requireNonBlank(name, "name");
        this.bucket = requireNonBlank(bucket, "bucket");
        this.keyPrefix = normaliseKeyPrefix(requireNonBlank(keyPrefix, "keyPrefix"));
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
        this.writerId = requireNonBlank(writerId, "writerId");

        final Path root = Objects.requireNonNull(localRoot, "localRoot")
                .toAbsolutePath()
                .normalize();
        this.localStagingRoot = root.resolve(STAGING_DIR_NAME).resolve(this.writerId);
        this.localCacheRoot = root.resolve(CACHE_DIR_NAME);

        try {
            Files.createDirectories(this.localStagingRoot);
            Files.createDirectories(this.localCacheRoot);
        } catch (final IOException e) {
            throw new UncheckedIOException(
                    "Unable to initialise S3 file store " + name + " local dirs at " + root, e);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    public String getBucket() {
        return bucket;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    @Override
    public FileStoreWrite newWrite() throws IOException {
        Files.createDirectories(localStagingRoot);
        final Path tempPath = Files.createTempDirectory(localStagingRoot, "write-");
        final String fileGroupKey = keyPrefix + writerId + "/" + formatId(sequence.incrementAndGet());
        return new S3FileStoreWrite(tempPath, fileGroupKey, false);
    }

    @Override
    public FileStoreWrite newDeterministicWrite(final String fileGroupId) throws IOException {
        Objects.requireNonNull(fileGroupId, "fileGroupId");
        if (fileGroupId.isBlank()) {
            throw new IllegalArgumentException("fileGroupId must not be blank");
        }

        final String fileGroupKey = keyPrefix + writerId + "/" + fileGroupId;

        // Check if already committed in S3.
        if (isCommittedInS3(fileGroupKey)) {
            LOGGER.debug("Deterministic write for '{}' already committed in S3", fileGroupKey);
            return new PreCommittedS3FileStoreWrite(fileGroupKey);
        }

        Files.createDirectories(localStagingRoot);
        final Path tempPath = Files.createTempDirectory(localStagingRoot, "write-");
        return new S3FileStoreWrite(tempPath, fileGroupKey, true);
    }

    @Override
    public Path resolve(final FileStoreLocation location) throws IOException {
        Objects.requireNonNull(location, "location");

        if (!name.equals(location.storeName())) {
            throw new IOException("File store location is for store '" + location.storeName()
                                  + "' but this store is '" + name + "'");
        }
        if (!location.isS3()) {
            throw new IOException("Unsupported file store location type: " + location.locationType());
        }

        final String locationBucket = location.getS3Bucket();
        final String locationKeyPrefix = location.getS3KeyPrefix();

        if (!bucket.equals(locationBucket)) {
            throw new IOException("File store location bucket '" + locationBucket
                                  + "' does not match store bucket '" + bucket + "'");
        }

        // Download all objects under the key prefix to a local cache directory.
        // Use a hash of the key prefix for the local cache dir name.
        final String cacheId = locationKeyPrefix.replace('/', '_');
        final Path cacheDir = localCacheRoot.resolve(cacheId);
        Files.createDirectories(cacheDir);

        final ListObjectsV2Response listResponse = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(normaliseKeyPrefix(locationKeyPrefix))
                        .build());

        for (final S3Object s3Object : listResponse.contents()) {
            final String objectKey = s3Object.key();
            // Extract the filename from the full key.
            final String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
            if (fileName.isEmpty() || COMMITTED_MARKER.equals(fileName)) {
                continue; // Skip the marker and any "directory" keys.
            }

            final Path localFile = cacheDir.resolve(fileName);
            if (!Files.exists(localFile)) {
                LOGGER.debug("Downloading s3://{}/{} -> {}", bucket, objectKey, localFile);
                s3Client.getObject(
                        GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .build(),
                        localFile);
            }
        }

        return cacheDir;
    }

    @Override
    public void delete(final FileStoreLocation location) throws IOException {
        Objects.requireNonNull(location, "location");

        if (!name.equals(location.storeName())) {
            throw new IOException("File store location is for store '" + location.storeName()
                                  + "' but this store is '" + name + "'");
        }
        if (!location.isS3()) {
            throw new IOException("Unsupported file store location type: " + location.locationType());
        }

        final String locationKeyPrefix = location.getS3KeyPrefix();

        // Delete all objects under the key prefix.
        final ListObjectsV2Response listResponse = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(normaliseKeyPrefix(locationKeyPrefix))
                        .build());

        for (final S3Object s3Object : listResponse.contents()) {
            LOGGER.debug("Deleting s3://{}/{}", bucket, s3Object.key());
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(s3Object.key())
                            .build());
        }

        // Also clean up any local cache entry.
        final String cacheId = locationKeyPrefix.replace('/', '_');
        final Path cacheDir = localCacheRoot.resolve(cacheId);
        if (Files.exists(cacheDir)) {
            deleteRecursively(cacheDir);
        }
    }

    @Override
    public boolean isComplete(final FileStoreLocation location) throws IOException {
        Objects.requireNonNull(location, "location");

        if (!name.equals(location.storeName())) {
            throw new IOException("File store location is for store '" + location.storeName()
                                  + "' but this store is '" + name + "'");
        }
        if (!location.isS3()) {
            return false;
        }

        final String locationKeyPrefix = location.getS3KeyPrefix();
        return isCommittedInS3(normaliseKeyPrefix(locationKeyPrefix));
    }

    // --- Internal helpers ---

    private boolean isCommittedInS3(final String fileGroupKeyPrefix) {
        final String markerKey = normaliseKeyPrefix(fileGroupKeyPrefix) + COMMITTED_MARKER;
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(markerKey)
                    .build());
            return true;
        } catch (final NoSuchKeyException e) {
            return false;
        }
    }

    private void uploadDirectory(final Path dir, final String targetKeyPrefix) throws IOException {
        final String normalisedPrefix = normaliseKeyPrefix(targetKeyPrefix);

        try (final Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .forEach(file -> {
                        final String key = normalisedPrefix + file.getFileName().toString();
                        LOGGER.debug("Uploading {} -> s3://{}/{}", file, bucket, key);
                        s3Client.putObject(
                                PutObjectRequest.builder()
                                        .bucket(bucket)
                                        .key(key)
                                        .build(),
                                file);
                    });
        }

        // Write the commit marker.
        final Path markerFile = dir.resolve(COMMITTED_MARKER);
        Files.writeString(markerFile, "");
        final String markerKey = normalisedPrefix + COMMITTED_MARKER;
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(markerKey)
                        .build(),
                markerFile);
    }

    private static S3Client buildS3Client(final FileStoreDefinition definition) {
        final S3ClientBuilder builder = S3Client.builder();

        if (definition.getRegion() != null) {
            builder.region(Region.of(definition.getRegion()));
        }
        if (definition.getEndpointOverride() != null) {
            builder.endpointOverride(URI.create(definition.getEndpointOverride()));
            builder.forcePathStyle(true); // Required for most S3-compatible stores.
        }

        builder.credentialsProvider(buildCredentialsProvider(definition));
        return builder.build();
    }

    private static AwsCredentialsProvider buildCredentialsProvider(final FileStoreDefinition definition) {
        final String type = definition.getEffectiveCredentialsType();
        return switch (type.toLowerCase()) {
            case "basic" -> {
                final String accessKey = requireNonBlank(definition.getAccessKeyId(), "accessKeyId");
                final String secretKey = requireNonBlank(definition.getSecretAccessKey(), "secretAccessKey");
                yield StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey));
            }
            case "environment" -> EnvironmentVariableCredentialsProvider.create();
            case "profile" -> ProfileCredentialsProvider.create();
            default -> DefaultCredentialsProvider.create();
        };
    }

    private static String normaliseKeyPrefix(final String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private static String formatId(final long id) {
        final String value = Long.toString(id);
        if (value.length() >= ID_WIDTH) {
            return value;
        }
        return "0".repeat(ID_WIDTH - value.length()) + value;
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static void deleteRecursively(final Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }

        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(final Path file,
                                             final BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir,
                                                       final IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // --- Write handles ---

    /**
     * Write handle that stages files locally and uploads to S3 on commit.
     */
    private final class S3FileStoreWrite implements FileStoreWrite {

        private final Path tempPath;
        private final String fileGroupKey;
        private final boolean deterministic;
        private boolean complete;

        private S3FileStoreWrite(final Path tempPath,
                                 final String fileGroupKey,
                                 final boolean deterministic) {
            this.tempPath = Objects.requireNonNull(tempPath, "tempPath");
            this.fileGroupKey = Objects.requireNonNull(fileGroupKey, "fileGroupKey");
            this.deterministic = deterministic;
        }

        @Override
        public Path getPath() {
            return tempPath;
        }

        @Override
        public FileStoreLocation commit() throws IOException {
            if (complete) {
                return FileStoreLocation.s3(name, bucket, fileGroupKey);
            }

            uploadDirectory(tempPath, fileGroupKey);

            // Clean up local staging.
            deleteRecursively(tempPath);

            complete = true;
            return FileStoreLocation.s3(name, bucket, fileGroupKey);
        }

        @Override
        public boolean isCommitted() {
            return complete;
        }

        @Override
        public void close() throws IOException {
            if (!complete) {
                deleteRecursively(tempPath);
            }
        }
    }

    /**
     * Pre-committed write handle returned when a deterministic write
     * target already exists in S3.
     */
    private final class PreCommittedS3FileStoreWrite implements FileStoreWrite {

        private final String fileGroupKey;

        private PreCommittedS3FileStoreWrite(final String fileGroupKey) {
            this.fileGroupKey = Objects.requireNonNull(fileGroupKey, "fileGroupKey");
        }

        @Override
        public Path getPath() {
            // No local staging path — this write is already committed.
            // Return a placeholder; callers should check isCommitted() first.
            return localStagingRoot.resolve("pre-committed-" + fileGroupKey.hashCode());
        }

        @Override
        public FileStoreLocation commit() {
            return FileStoreLocation.s3(name, bucket, fileGroupKey);
        }

        @Override
        public boolean isCommitted() {
            return true;
        }

        @Override
        public void close() {
            // Nothing to clean up — the data is already committed in S3.
        }
    }
}
