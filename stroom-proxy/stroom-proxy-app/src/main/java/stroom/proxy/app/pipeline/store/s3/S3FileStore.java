/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.store.s3;

import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;

import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Set;
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
 * </pre>
 * </p>
 */
public class S3FileStore implements FileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3FileStore.class);


    /**
     * Shared default credentials provider.
     * <p>
     * {@code DefaultCredentialsProvider.create()} is deprecated - it returns a shared
     * singleton that no caller can safely close. The builder gives an instance the
     * caller owns, so it is created once here and reused across stores rather than
     * once per store, each of which would start its own credential refresh.
     * </p>
     */
    private static final AwsCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER =
            DefaultCredentialsProvider.builder().build();

    /** Recognised values for {@code credentialsType}, lower case. */
    public static final Set<String> SUPPORTED_CREDENTIALS_TYPES =
            Set.of("default", "basic", "environment");

    private static final String CACHE_DIR_NAME = "cache";
    private static final String STAGING_DIR_NAME = "staging";
    /**
     * Suffix for an in-progress cache download. A file carrying it is by definition incomplete, so it
     * is never mistaken for a cache hit.
     */
    private static final String PART_SUFFIX = ".part";

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
    public HealthCheck.Result healthCheck() {
        try {
            // Check S3 bucket accessibility.
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(bucket)
                    .build());

            // Check local staging and cache directories.
            final boolean stagingOk = Files.isDirectory(localStagingRoot)
                                      && Files.isWritable(localStagingRoot);
            final boolean cacheOk = Files.isDirectory(localCacheRoot)
                                    && Files.isWritable(localCacheRoot);

            if (!stagingOk || !cacheOk) {
                return HealthCheck.Result.builder()
                        .unhealthy()
                        .withMessage("Local directory check failed: staging=%s, cache=%s",
                                stagingOk, cacheOk)
                        .build();
            }

            return HealthCheck.Result.builder()
                    .healthy()
                    .withDetail("bucket", bucket)
                    .withDetail("keyPrefix", keyPrefix)
                    .withDetail("localStagingWritable", true)
                    .build();

        } catch (final Exception e) {
            return HealthCheck.Result.builder()
                    .unhealthy()
                    .withMessage("S3 health check failed for bucket '%s': %s",
                            bucket, e.getMessage())
                    .build();
        }
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

        // Check if already present in S3.
        if (hasObjectsInS3(fileGroupKey)) {
            LOGGER.debug("Deterministic write for '{}' already exists in S3", fileGroupKey);
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
            if (fileName.isEmpty()) {
                continue; // Skip any "directory" keys.
            }

            final Path localFile = cacheDir.resolve(fileName);
            if (!Files.exists(localFile)) {
                // Download to a .part sibling and rename on completion, so that presence at the cache
                // path means the file is complete. Existence was previously the only validity test, so
                // a file left short by a kill mid-getObject - the cache dir name is stable across
                // restarts and is not partitioned by writerId - was reused as if whole for the life of
                // the deployment. Downstream that is corruption rather than loss: validateFileGroup
                // only asserts isRegularFile, so a truncated zip is forwarded and acknowledged, and the
                // good copy in S3 is then deleted.
                final Path partFile = cacheDir.resolve(fileName + PART_SUFFIX);
                Files.deleteIfExists(partFile);
                LOGGER.debug("Downloading s3://{}/{} -> {}", bucket, objectKey, partFile);
                s3Client.getObject(
                        GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .build(),
                        partFile);
                Files.move(partFile, localFile, StandardCopyOption.ATOMIC_MOVE);
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


    // --- Internal helpers ---

    private boolean hasObjectsInS3(final String fileGroupKeyPrefix) {
        final ListObjectsV2Response response = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucket)
                        .prefix(normaliseKeyPrefix(fileGroupKeyPrefix))
                        .maxKeys(1)
                        .build());
        return response.hasContents() && !response.contents().isEmpty();
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

    /**
     * Build the credentials provider for this store.
     * <p>
     * The intended model is that identity belongs to the workload, not to
     * configuration: a pod, task or instance carries an IAM role scoped to the
     * stages it runs, and {@code default} picks that up through the SDK chain
     * (IRSA/web identity, container credentials, or instance profile). Because
     * stages can be split across nodes, each node's role can be scoped to just the
     * stores and queues that stage touches, which is finer-grained than per-store
     * credentials in one process would be - and keeps secrets out of the config.
     * </p>
     * <p>
     * {@code basic} exists for S3-compatible endpoints such as MinIO or LocalStack,
     * which have no instance identity and can only be reached with static keys.
     * </p>
     * <p>
     * There is deliberately no {@code profile} option. The SDK could support one -
     * {@code ProfileCredentialsProvider.create(String)} takes a profile name - but a
     * per-store profile is the wrong shape for this system: it would put a second,
     * competing notion of identity in the config file alongside the workload's own
     * role. Set {@code AWS_PROFILE} in the environment if a named profile is needed;
     * the default chain honours it.
     * </p>
     * <p>
     * Note the option that was removed never selected a profile anyway - it called
     * the no-argument {@code ProfileCredentialsProvider.create()} and there was no
     * {@code profileName} property to pass, so it resolved the same profile
     * {@code default} would.
     * </p>
     *
     * @throws IllegalArgumentException If the configured type is not recognised.
     */
    private static AwsCredentialsProvider buildCredentialsProvider(final FileStoreDefinition definition) {
        final String type = definition.getEffectiveCredentialsType();
        return switch (type.toLowerCase()) {
            case "default" -> DEFAULT_CREDENTIALS_PROVIDER;
            case "basic" -> {
                final String accessKey = requireNonBlank(definition.getAccessKeyId(), "accessKeyId");
                final String secretKey = requireNonBlank(definition.getSecretAccessKey(), "secretAccessKey");
                yield StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey));
            }
            case "environment" -> EnvironmentVariableCredentialsProvider.create();
            default -> throw new IllegalArgumentException(
                    "Unsupported credentialsType '" + type + "' for S3 file store '" + definition.getBucket()
                    + "'. Supported types are: " + String.join(", ", SUPPORTED_CREDENTIALS_TYPES));
        };
    }

    private static String normaliseKeyPrefix(final String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return "";
        }
        return prefix.endsWith("/")
                ? prefix
                : prefix + "/";
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
