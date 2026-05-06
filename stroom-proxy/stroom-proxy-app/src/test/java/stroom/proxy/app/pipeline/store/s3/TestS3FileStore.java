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

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(location.storeName()).isEqualTo(STORE_NAME);
        assertThat(location.locationType()).isEqualTo(FileStoreLocation.LocationType.S3);
        assertThat(location.uri()).isEqualTo("s3://my-bucket/prefix/subdir");
        assertThat(location.isS3()).isTrue();
        assertThat(location.isLocalFileSystem()).isFalse();
        assertThat(location.getS3Bucket()).isEqualTo("my-bucket");
        assertThat(location.getS3KeyPrefix()).isEqualTo("prefix/subdir");
    }

    @Test
    void testS3LocationRequiresS3Scheme() {
        assertThatThrownBy(() ->
                new FileStoreLocation(STORE_NAME, FileStoreLocation.LocationType.S3,
                        "file:///some/path", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testResolveLocalFilesystemLocationFails() {
        final FileStoreLocation localLocation = FileStoreLocation.localFileSystem(
                STORE_NAME, tempDir.resolve("some-path"));

        assertThatThrownBy(() -> s3FileStore.resolve(localLocation))
                .isInstanceOf(IOException.class);
    }
}
