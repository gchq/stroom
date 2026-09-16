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

import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.util.io.FileUtil;

import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * A {@link FileStore} on S3, or an S3-compatible object store. Shared mode only.
 * <p>
 * Layout, with one writer root per process start exactly as on a shared mount:
 * </p>
 * <pre>
 * {@code s3://<bucket>/<prefix>/<startId>/<id>/proxy.meta
 * s3://<bucket>/<prefix>/<startId>/<id>/<part>/proxy.zip   <- a nested group is a longer key
 * s3://<bucket>/<prefix>/<startId>/<id>/.complete          <- marker, PUT last}
 * </pre>
 * <p>
 * A group is several objects PUT one at a time, so no individual object proves the rest arrived.
 * The marker is PUT only once every other PUT has been acknowledged; its own PUT is atomic, so its
 * presence is completeness and its absence is an interrupted upload that no reader accepts.
 * </p>
 * <p>
 * Local scratch - staging before upload, a download per resolve - is on this node's own disk and
 * cleared at start-up. Objects left in the bucket by a crash are cleared by a lifecycle rule the
 * operator configures; this store runs nothing on a timer.
 * </p>
 */
public class S3FileStore implements FileStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3FileStore.class);

    /**
     * {@code DefaultCredentialsProvider.create()} returns a shared singleton no caller can safely
     * close; the builder gives an instance created once here and reused across stores.
     */
    private static final AwsCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER =
            DefaultCredentialsProvider.builder().build();

    /** Recognised values for {@code credentialsType}, lower case. */
    public static final Set<String> SUPPORTED_CREDENTIALS_TYPES = Set.of("default", "basic", "environment");

    static final String COMPLETE_MARKER_NAME = ".complete";
    private static final String STAGING_DIR_NAME = "staging";
    private static final String RESOLVE_DIR_NAME = "resolve";
    private static final int ID_WIDTH = 12;

    private final String name;
    private final String bucket;
    private final String keyPrefix;
    private final String writerPrefix;
    private final S3Client s3Client;
    private final Path localStagingRoot;
    private final Path localResolveRoot;
    private final AtomicLong sequence = new AtomicLong();

    public S3FileStore(final String name,
                       final FileStoreDefinition definition,
                       final Path localRoot) {
        this(name,
                definition.getBucket(),
                definition.getEffectiveKeyPrefix(name),
                buildS3Client(definition),
                localRoot);
    }

    public S3FileStore(final String name,
                       final String bucket,
                       final String keyPrefix,
                       final S3Client s3Client,
                       final Path localRoot) {
        this.name = requireNonBlank(name, "name");
        this.bucket = requireNonBlank(bucket, "bucket");
        this.keyPrefix = withTrailingSlash(requireNonBlank(keyPrefix, "keyPrefix"));
        this.writerPrefix = this.keyPrefix + UUID.randomUUID() + "/";
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");

        final Path root = Objects.requireNonNull(localRoot, "localRoot").toAbsolutePath().normalize();
        this.localStagingRoot = root.resolve(STAGING_DIR_NAME);
        this.localResolveRoot = root.resolve(RESOLVE_DIR_NAME);
        try {
            // Node-local disk with one process on it: a staging directory belongs to a write that
            // never committed, a resolve directory to a caller that never consumed it, and neither is
            // the last copy of anything.
            FileUtil.deleteContents(localStagingRoot);
            FileUtil.deleteContents(localResolveRoot);
            Files.createDirectories(localStagingRoot);
            Files.createDirectories(localResolveRoot);
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to initialise S3 file store " + name + " local dirs at " + root, e);
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
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            final boolean stagingOk = Files.isDirectory(localStagingRoot) && Files.isWritable(localStagingRoot);
            final boolean resolveOk = Files.isDirectory(localResolveRoot) && Files.isWritable(localResolveRoot);
            if (!stagingOk || !resolveOk) {
                return HealthCheck.Result.builder()
                        .unhealthy()
                        .withMessage("Local directory check failed: staging=%s, resolve=%s", stagingOk, resolveOk)
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
                    .withMessage("S3 health check failed for bucket '%s': %s", bucket, e.getMessage())
                    .build();
        }
    }

    @Override
    public FileStoreWrite newWrite() throws IOException {
        final String id = formatId(sequence.incrementAndGet());
        final Path staging = localStagingRoot.resolve(id);
        Files.createDirectories(staging);
        return new Write(staging, writerPrefix + id + "/");
    }

    @Override
    public Path resolve(final FileStoreLocation location) throws IOException {
        final String groupPrefix = toGroupPrefix(location);
        final List<S3Object> objects = listAllObjects(groupPrefix);

        if (!hasMarker(objects, groupPrefix)) {
            throw new FileGroupNotFoundException(location, "No complete file group at '" + location.uri() + "'. "
                    + (objects.isEmpty()
                    ? "Nothing is stored under that key; it was never written, or it has already been "
                      + "consumed and deleted."
                    : "Objects exist under that key but the '" + COMPLETE_MARKER_NAME
                      + "' marker that publishes them does not, so the upload did not finish."));
        }

        // A fresh directory per resolve, so a partial download can never be mistaken for a complete
        // one: it is discarded with the directory, and the start-up sweep reclaims it if this throws
        // after the directory exists.
        final Path resolveDir = Files.createTempDirectory(localResolveRoot, "resolve-");
        try {
            for (final S3Object object : objects) {
                final String relative = object.key().substring(groupPrefix.length());
                if (relative.isEmpty() || COMPLETE_MARKER_NAME.equals(relative)) {
                    continue;
                }
                final Path localFile = resolveDir.resolve(relative).normalize();
                if (!localFile.startsWith(resolveDir)) {
                    throw new IOException("Object key '" + object.key() + "' escapes the resolve directory");
                }
                Files.createDirectories(localFile.getParent());
                s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(object.key()).build(), localFile);
            }
        } catch (final RuntimeException | IOException e) {
            // Nothing has been handed to anyone yet, so this is ours to remove. Best effort; a failure
            // here must not replace the download failure the caller needs to see.
            if (!FileUtil.deleteDir(resolveDir)) {
                LOGGER.warn("Failed to remove the partial resolve directory {} after a failed download",
                        FileUtil.getCanonicalPath(resolveDir));
            }
            throw e;
        }
        return resolveDir;
    }

    @Override
    public void delete(final FileStoreLocation location) throws IOException {
        final String groupPrefix = toGroupPrefix(location);
        final List<S3Object> objects = listAllObjects(groupPrefix);

        // The marker first, the mirror of writing it last. A delete interrupted part way then leaves
        // something that reads as incomplete rather than as a complete but short group.
        final String markerKey = groupPrefix + COMPLETE_MARKER_NAME;
        for (final S3Object object : objects) {
            if (markerKey.equals(object.key())) {
                deleteObject(object.key());
            }
        }
        for (final S3Object object : objects) {
            if (!markerKey.equals(object.key())) {
                deleteObject(object.key());
            }
        }
    }

    @Override
    public void close() {
        s3Client.close();
    }

    /**
     * Validate a location and return the group's key prefix with a trailing slash. Every location
     * must name this store, this bucket and a key within this store's prefix: a location with an
     * empty key would address the whole bucket, and locations arrive from queue messages that may
     * have been written by another process.
     */
    private String toGroupPrefix(final FileStoreLocation location) throws IOException {
        Objects.requireNonNull(location, "location");
        if (!name.equals(location.storeName())) {
            throw new IOException("File store location is for store '" + location.storeName()
                                  + "' but this store is '" + name + "'");
        }
        if (!location.isS3()) {
            throw new IOException("Not an S3 location: " + location.uri());
        }
        if (!bucket.equals(location.getS3Bucket())) {
            throw new IOException("File store location bucket '" + location.getS3Bucket()
                                  + "' does not match store bucket '" + bucket + "'");
        }
        final String key = location.getS3Key();
        if (key.isEmpty()) {
            throw new IOException("File store location '" + location.uri() + "' has no key, which would "
                                  + "address the whole of bucket '" + bucket + "'");
        }
        final String groupPrefix = withTrailingSlash(key);
        if (!groupPrefix.startsWith(keyPrefix) || groupPrefix.equals(keyPrefix)) {
            throw new IOException("File store location key '" + key + "' is outside this store's key prefix '"
                                  + keyPrefix + "'");
        }
        return groupPrefix;
    }

    /**
     * Only the marker at the group's own prefix counts. A nested group may legitimately contain a
     * file of the same name deeper down, and that proves nothing about this upload.
     */
    private static boolean hasMarker(final List<S3Object> objects, final String groupPrefix) {
        final String markerKey = groupPrefix + COMPLETE_MARKER_NAME;
        return objects.stream().anyMatch(object -> markerKey.equals(object.key()));
    }

    /**
     * Every object under a prefix, following continuation tokens: S3 caps a page at 1000 keys and
     * the truncation is otherwise invisible.
     */
    private List<S3Object> listAllObjects(final String prefix) {
        final List<S3Object> objects = new ArrayList<>();
        String continuationToken = null;
        do {
            final ListObjectsV2Response response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(continuationToken)
                    .build());
            objects.addAll(response.contents());
            continuationToken = Boolean.TRUE.equals(response.isTruncated())
                    ? response.nextContinuationToken()
                    : null;
        } while (continuationToken != null);
        return objects;
    }

    private void deleteObject(final String key) {
        LOGGER.debug("Deleting s3://{}/{}", bucket, key);
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /**
     * PUT every file in the tree under the group prefix, preserving relative paths, then the marker.
     */
    private void uploadTree(final Path dir, final String groupPrefix) throws IOException {
        final List<Path> files;
        try (final Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        for (final Path file : files) {
            final String key = groupPrefix + dir.relativize(file).toString().replace('\\', '/');
            LOGGER.debug("Uploading {} -> s3://{}/{}", file, bucket, key);
            s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), file);
        }
        final String markerKey = groupPrefix + COMPLETE_MARKER_NAME;
        LOGGER.debug("Marking s3://{}/{} complete", bucket, markerKey);
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(markerKey).build(), RequestBody.empty());
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
     * Identity belongs to the workload, not to configuration: a pod, task or instance carries an IAM
     * role scoped to the stages it runs, and {@code default} picks that up through the SDK chain.
     * {@code basic} exists for S3-compatible endpoints such as MinIO, which have no instance
     * identity. There is deliberately no {@code profile} option; set {@code AWS_PROFILE} in the
     * environment and the default chain honours it.
     *
     * @throws IllegalArgumentException If the configured type is not recognised.
     */
    private static AwsCredentialsProvider buildCredentialsProvider(final FileStoreDefinition definition) {
        final String type = definition.getEffectiveCredentialsType();
        return switch (type.toLowerCase()) {
            case "default" -> DEFAULT_CREDENTIALS_PROVIDER;
            case "basic" -> StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    requireNonBlank(definition.getAccessKeyId(), "accessKeyId"),
                    requireNonBlank(definition.getSecretAccessKey(), "secretAccessKey")));
            case "environment" -> EnvironmentVariableCredentialsProvider.create();
            default -> throw new IllegalArgumentException(
                    "Unsupported credentialsType '" + type + "' for S3 file store '" + definition.getBucket()
                    + "'. Supported types are: " + String.join(", ", SUPPORTED_CREDENTIALS_TYPES));
        };
    }

    private static String withTrailingSlash(final String prefix) {
        return prefix.endsWith("/")
                ? prefix
                : prefix + "/";
    }

    private static String formatId(final long id) {
        final String value = Long.toString(id);
        return value.length() >= ID_WIDTH
                ? value
                : "0".repeat(ID_WIDTH - value.length()) + value;
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    @Override
    public String toString() {
        return "S3FileStore{name='" + name + "', bucket='" + bucket + "', keyPrefix='" + keyPrefix + "'}";
    }


    // --------------------------------------------------------------------------------


    private final class Write implements FileStoreWrite {

        private final Path staging;
        private final String groupPrefix;
        private boolean committed;

        private Write(final Path staging, final String groupPrefix) {
            this.staging = staging;
            this.groupPrefix = groupPrefix;
        }

        @Override
        public Path getPath() {
            return staging;
        }

        @Override
        public FileStoreLocation commit() throws IOException {
            if (committed) {
                return FileStoreLocation.s3(name, bucket, groupPrefix);
            }

            uploadTree(staging, groupPrefix);
            committed = true;

            // The upload is acknowledged, so the commit has happened; a failure to remove local
            // staging is disk left behind, not a failed commit.
            if (!FileUtil.deleteDir(staging)) {
                LOGGER.warn("Failed to fully delete the local staging directory {} after uploading it to "
                            + "s3://{}/{}. The upload succeeded and the commit stands.",
                        FileUtil.getCanonicalPath(staging), bucket, groupPrefix);
            }
            return FileStoreLocation.s3(name, bucket, groupPrefix);
        }

        @Override
        public void close() {
            if (!committed && !FileUtil.deleteDir(staging)) {
                LOGGER.warn("Failed to fully delete the staging directory {} of an uncommitted write",
                        FileUtil.getCanonicalPath(staging));
            }
        }
    }
}
