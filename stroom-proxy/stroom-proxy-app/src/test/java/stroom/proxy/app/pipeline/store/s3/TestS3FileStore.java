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

import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S3-specific tests for {@link S3FileStore} that are not part of the
 * general {@link FileStore} contract (which is tested by
 * {@link TestS3FileStoreContract}).
 * <p>
 * These tests validate S3 location factory methods, URI scheme
 * validation, and S3 store's rejection of non-S3 location types.
 * </p>
 */
class TestS3FileStore {

    private static final String STORE_NAME = "testStore";
    private static final String BUCKET = "test-bucket";
    private static final String KEY_PREFIX = "testStore/";
    private static final String WRITER_ID = "test-writer";

    @TempDir
    Path tempDir;

    private S3FileStore s3FileStore;

    @BeforeEach
    void setUp() {
        final StubS3Client stubS3Client = new StubS3Client(tempDir.resolve("s3-backing"));
        s3FileStore = new S3FileStore(
                STORE_NAME,
                BUCKET,
                KEY_PREFIX,
                stubS3Client,
                tempDir.resolve("local-root"),
                WRITER_ID);
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

    @Test
    void testResolveLocalFilesystemLocationFails() {
        final FileStoreLocation localLocation = FileStoreLocation.localFileSystem(
                STORE_NAME, tempDir.resolve("some-path"));

        assertThrows(java.io.IOException.class, () -> s3FileStore.resolve(localLocation));
    }
}
