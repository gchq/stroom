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

package stroom.proxy.app.pipeline.store.s3;

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
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final Map<String, Path> objects = new ConcurrentHashMap<>();

    StubS3Client(final Path backingDir) {
        this.backingDir = backingDir;
        try {
            Files.createDirectories(backingDir);
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
        return ListObjectsV2Response.builder()
                .contents(matching)
                .build();
    }

    @Override
    public DeleteObjectResponse deleteObject(final DeleteObjectRequest request) {
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
        // Nothing to close.
    }

    private static long fileSize(final Path path) {
        try {
            return Files.size(path);
        } catch (final IOException e) {
            return 0;
        }
    }
}
