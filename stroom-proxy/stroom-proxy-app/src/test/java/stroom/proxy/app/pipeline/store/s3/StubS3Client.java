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

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A minimal S3 client stub that stores objects as files on the local
 * filesystem. Only implements the methods used by {@link S3FileStore}.
 * <p>
 * Shared between {@link TestS3FileStore} and
 * {@link TestS3FileStoreContract}.
 * </p>
 */
class StubS3Client implements S3Client {

    private final Path backingDir;

    private final List<String> deletionOrder = new ArrayList<>();
    private final List<String> putOrder = new ArrayList<>();
    private int pageSize = Integer.MAX_VALUE;
    private boolean closed;
    private final Map<String, Path> objects = new ConcurrentHashMap<>();

    StubS3Client(final Path backingDir) {
        this.backingDir = backingDir;
        try {
            Files.createDirectories(backingDir);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove every object whose key ends with the given suffix, to simulate a crash partway through
     * writing a multi-object group. Matched by suffix rather than by an exact key so a test does not
     * have to reconstruct the store's key layout.
     *
     * @return the number removed.
     */
    int removeObjectsEndingWith(final String suffix) {
        final var matching = objects.keySet().stream().filter(k -> k.endsWith(suffix)).toList();
        matching.forEach(objects::remove);
        return matching.size();
    }

    /** Make listings paginate, so a test can prove continuation tokens are followed. */
    void setPageSize(final int pageSize) {
        this.pageSize = pageSize;
    }

    boolean isClosed() {
        return closed;
    }

    /** Keys PUT, in order, so a test can assert the marker went last. */
    List<String> getPutOrder() {
        return putOrder;
    }

    /** Keys deleted, in order, so a test can assert deletion ordering. */
    List<String> getDeletionOrder() {
        return deletionOrder;
    }

    /** Plant an object at an exact key, to simulate one this store would not itself write. */
    void putObjectDirectly(final String bucketAndKey, final String content) {
        try {
            final Path dest = backingDir.resolve(bucketAndKey.replace('/', '_'));
            Files.createDirectories(dest.getParent());
            Files.writeString(dest, content);
            objects.put(bucketAndKey, dest);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    int objectCount() {
        return objects.size();
    }

    @Override
    public PutObjectResponse putObject(final PutObjectRequest request,
                                       final Path source) {
        final String key = request.bucket() + "/" + request.key();
        putOrder.add(request.key());
        try {
            final Path dest = backingDir.resolve(key.replace('/', '_'));
            Files.createDirectories(dest.getParent());
            Files.copy(source, dest);
            objects.put(key, dest);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return PutObjectResponse.builder().build();
    }

    /**
     * The {@link RequestBody} overload, used for the zero-byte completeness marker (R10, §3.3).
     * The marker has no source file, so it cannot go through the {@link Path} overload above.
     */
    @Override
    public PutObjectResponse putObject(final PutObjectRequest request,
                                       final RequestBody body) {
        final String key = request.bucket() + "/" + request.key();
        putOrder.add(request.key());
        try {
            final Path dest = backingDir.resolve(key.replace('/', '_'));
            Files.createDirectories(dest.getParent());
            try (final InputStream in = body.contentStreamProvider().newStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            objects.put(key, dest);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return PutObjectResponse.builder().build();
    }

    @Override
    public GetObjectResponse getObject(final GetObjectRequest request,
                                       final Path destination) {
        final String key = request.bucket() + "/" + request.key();
        final Path source = objects.get(key);
        if (source == null) {
            throw NoSuchKeyException.builder()
                    .message("No such key: " + key)
                    .build();
        }
        try {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return GetObjectResponse.builder().build();
    }

    @Override
    public HeadBucketResponse headBucket(final HeadBucketRequest request) {
        // Stub always reports bucket as accessible.
        return HeadBucketResponse.builder().build();
    }

    @Override
    public HeadObjectResponse headObject(final HeadObjectRequest request) {
        final String key = request.bucket() + "/" + request.key();
        if (objects.containsKey(key)) {
            return HeadObjectResponse.builder().build();
        }
        throw NoSuchKeyException.builder()
                .message("No such key: " + key)
                .build();
    }

    @Override
    public ListObjectsV2Response listObjectsV2(final ListObjectsV2Request request) {
        final String prefix = request.bucket() + "/"
                              + (request.prefix() != null ? request.prefix() : "");
        final List<S3Object> matching = new ArrayList<>();
        for (final Map.Entry<String, Path> entry : objects.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                // Extract just the key part (without bucket).
                final String fullKey = entry.getKey();
                final String keyOnly = fullKey.substring(request.bucket().length() + 1);
                matching.add(S3Object.builder()
                        .key(keyOnly)
                        .size(fileSize(entry.getValue()))
                        .build());
            }
        }
        matching.sort(java.util.Comparator.comparing(S3Object::key));
        int from = 0;
        if (request.continuationToken() != null) {
            from = Integer.parseInt(request.continuationToken());
        }
        final int to = Math.min(matching.size(), from + pageSize);
        final boolean truncated = to < matching.size();
        return ListObjectsV2Response.builder()
                .contents(matching.subList(from, to))
                .isTruncated(truncated)
                .nextContinuationToken(truncated ? Integer.toString(to) : null)
                .build();
    }

    @Override
    public DeleteObjectResponse deleteObject(final DeleteObjectRequest request) {
        deletionOrder.add(request.key());
        final String key = request.bucket() + "/" + request.key();
        final Path removed = objects.remove(key);
        if (removed != null) {
            try {
                Files.deleteIfExists(removed);
            } catch (final IOException e) {
                // Ignore cleanup errors in test stub.
            }
        }
        return DeleteObjectResponse.builder().build();
    }

    @Override
    public String serviceName() {
        return "s3-stub";
    }

    @Override
    public void close() {
        closed = true;
    }

    private static long fileSize(final Path path) {
        try {
            return Files.size(path);
        } catch (final IOException e) {
            return 0;
        }
    }
}
