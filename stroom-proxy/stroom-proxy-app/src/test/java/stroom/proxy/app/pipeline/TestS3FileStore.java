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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.services.s3.S3Client;
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
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link S3FileStore} using a stub S3 client that stores
 * objects in memory backed by a local directory.
 */
class TestS3FileStore {

    private static final String STORE_NAME = "testStore";
    private static final String BUCKET = "test-bucket";
    private static final String KEY_PREFIX = "testStore/";
    private static final String WRITER_ID = "test-writer";

    @TempDir
    Path tempDir;

    private StubS3Client stubS3Client;
    private S3FileStore s3FileStore;

    @BeforeEach
    void setUp() {
        stubS3Client = new StubS3Client(tempDir.resolve("s3-backing"));
        s3FileStore = new S3FileStore(
                STORE_NAME,
                BUCKET,
                KEY_PREFIX,
                stubS3Client,
                tempDir.resolve("local-root"),
                WRITER_ID);
    }

    @Test
    void testNewWriteAndCommit() throws IOException {
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            // Write some test files to the staging directory.
            Files.writeString(write.getPath().resolve("proxy.meta"), "Feed:TEST_FEED");
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip-content");

            assertFalse(write.isCommitted());

            final FileStoreLocation location = write.commit();

            assertTrue(write.isCommitted());
            assertNotNull(location);
            assertEquals(STORE_NAME, location.storeName());
            assertEquals(FileStoreLocation.LocationType.S3, location.locationType());
            assertTrue(location.uri().startsWith("s3://"));
            assertTrue(location.isS3());
            assertFalse(location.isLocalFileSystem());
        }

        // Verify objects were uploaded to the stub.
        assertTrue(stubS3Client.objectCount() >= 3, // proxy.meta, proxy.zip, .committed
                "Expected at least 3 objects, got " + stubS3Client.objectCount());
    }

    @Test
    void testWriteCommitResolveContent() throws IOException {
        // Write and commit a file group.
        final FileStoreLocation location;
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "Feed:RESOLVE_TEST");
            Files.writeString(write.getPath().resolve("proxy.zip"), "resolve-zip-content");
            location = write.commit();
        }

        // Resolve the location — should download files to local cache.
        final Path resolved = s3FileStore.resolve(location);

        assertTrue(Files.isDirectory(resolved));
        assertTrue(Files.exists(resolved.resolve("proxy.meta")));
        assertTrue(Files.exists(resolved.resolve("proxy.zip")));
        assertEquals("Feed:RESOLVE_TEST", Files.readString(resolved.resolve("proxy.meta")));
        assertEquals("resolve-zip-content", Files.readString(resolved.resolve("proxy.zip")));
    }

    @Test
    void testIsCompleteAfterCommit() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "complete-test");
            location = write.commit();
        }

        assertTrue(s3FileStore.isComplete(location));
    }

    @Test
    void testIsCompleteBeforeCommit() throws IOException {
        // Create a location pointing to a non-existent file group.
        final FileStoreLocation fakeLocation = FileStoreLocation.s3(
                STORE_NAME, BUCKET, KEY_PREFIX + "nonexistent/group");

        assertFalse(s3FileStore.isComplete(fakeLocation));
    }

    @Test
    void testDeleteRemovesObjects() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "delete-test");
            Files.writeString(write.getPath().resolve("proxy.zip"), "delete-zip");
            location = write.commit();
        }

        assertTrue(s3FileStore.isComplete(location));

        // Delete the file group.
        s3FileStore.delete(location);

        // Objects should be gone.
        assertFalse(s3FileStore.isComplete(location));
    }

    @Test
    void testDeleteIdempotent() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "idempotent");
            location = write.commit();
        }

        // Delete twice — should not throw.
        s3FileStore.delete(location);
        assertDoesNotThrow(() -> s3FileStore.delete(location));
    }

    @Test
    void testDeterministicWriteIdempotency() throws IOException {
        final String fileGroupId = "deterministic-001";

        // First write — creates and commits.
        final FileStoreLocation location1;
        try (final FileStoreWrite write = s3FileStore.newDeterministicWrite(fileGroupId)) {
            assertFalse(write.isCommitted());
            Files.writeString(write.getPath().resolve("proxy.meta"), "deterministic-data");
            location1 = write.commit();
        }

        // Second write — should detect the existing commit and return pre-committed.
        final FileStoreLocation location2;
        try (final FileStoreWrite write = s3FileStore.newDeterministicWrite(fileGroupId)) {
            assertTrue(write.isCommitted(), "Second deterministic write should be pre-committed");
            location2 = write.commit();
        }

        assertEquals(location1.uri(), location2.uri());
    }

    @Test
    void testUncommittedWriteCleanedOnClose() throws IOException {
        final Path stagingPath;
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            stagingPath = write.getPath();
            Files.writeString(stagingPath.resolve("proxy.meta"), "abandoned");
            // Don't commit — just close.
        }

        // Staging directory should have been cleaned up.
        assertFalse(Files.exists(stagingPath));
    }

    @Test
    void testCommitIdempotent() throws IOException {
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "commit-twice");
            final FileStoreLocation loc1 = write.commit();
            final FileStoreLocation loc2 = write.commit();

            assertEquals(loc1, loc2);
        }
    }

    @Test
    void testResolveMismatchedStoreName() {
        final FileStoreLocation badLocation = FileStoreLocation.s3(
                "otherStore", BUCKET, KEY_PREFIX + "some/key");

        assertThrows(IOException.class, () -> s3FileStore.resolve(badLocation));
    }

    @Test
    void testResolveLocalFilesystemLocationFails() {
        final FileStoreLocation localLocation = FileStoreLocation.localFileSystem(
                STORE_NAME, tempDir.resolve("some-path"));

        assertThrows(IOException.class, () -> s3FileStore.resolve(localLocation));
    }

    @Test
    void testS3LocationFactoryAndAccessors() {
        final FileStoreLocation location = FileStoreLocation.s3(
                STORE_NAME, "my-bucket", "prefix/subdir");

        assertEquals(STORE_NAME, location.storeName());
        assertEquals(FileStoreLocation.LocationType.S3, location.locationType());
        assertEquals("s3://my-bucket/prefix/subdir", location.uri());
        assertTrue(location.isS3());
        assertFalse(location.isLocalFileSystem());
        assertEquals("my-bucket", location.getS3Bucket());
        assertEquals("prefix/subdir", location.getS3KeyPrefix());
    }

    @Test
    void testS3LocationRequiresS3Scheme() {
        assertThrows(IllegalArgumentException.class, () ->
                new FileStoreLocation(STORE_NAME, FileStoreLocation.LocationType.S3,
                        "file:///some/path", Map.of()));
    }

    // -----------------------------------------------------------------------
    // Stub S3Client — stores objects in a local directory for testing
    // -----------------------------------------------------------------------

    /**
     * A minimal S3 client stub that stores objects as files on the local
     * filesystem. Only implements the methods used by S3FileStore.
     */
    static class StubS3Client implements S3Client {

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
}
