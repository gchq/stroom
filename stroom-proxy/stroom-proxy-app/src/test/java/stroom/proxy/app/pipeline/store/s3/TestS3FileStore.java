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

import stroom.proxy.app.pipeline.UndeletableDir;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.util.io.FileUtil;

import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What {@link S3FileStore} does beyond the {@link stroom.proxy.app.pipeline.store.FileStore} contract:
 * the completeness marker, the per-start writer prefix, the download per resolve, and the local
 * staging that a commit does not depend on.
 */
class TestS3FileStore {

    private static final String STORE_NAME = "testStore";
    private static final String BUCKET = "test-bucket";
    private static final String KEY_PREFIX = "testStore/";

    @TempDir
    Path tempDir;

    private S3FileStore s3FileStore;
    private StubS3Client stubS3Client;

    @BeforeEach
    void setUp() {
        stubS3Client = new StubS3Client(tempDir.resolve("s3-backing"));
        s3FileStore = new S3FileStore(STORE_NAME, BUCKET, KEY_PREFIX, stubS3Client, tempDir.resolve("local-root"));
    }

    @Test
    void testS3LocationFactoryAndAccessors() {
        final FileStoreLocation location = FileStoreLocation.s3(STORE_NAME, "my-bucket", "prefix/subdir/");

        assertThat(location.storeName()).isEqualTo(STORE_NAME);
        assertThat(location.uri()).isEqualTo("s3://my-bucket/prefix/subdir/");
        assertThat(location.isS3()).isTrue();
        assertThat(location.isFilesystem()).isFalse();
        assertThat(location.getS3Bucket()).isEqualTo("my-bucket");
        assertThat(location.getS3Key()).isEqualTo("prefix/subdir/");
    }

    @Test
    void testALocationMustCarryAKnownScheme() {
        assertThatThrownBy(() -> new FileStoreLocation(STORE_NAME, "http://elsewhere/path"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testAFilesystemLocationIsRejected() {
        final FileStoreLocation localLocation = FileStoreLocation.filesystem(STORE_NAME, tempDir.resolve("some-path"));
        assertThatThrownBy(() -> s3FileStore.resolve(localLocation)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> s3FileStore.delete(localLocation)).isInstanceOf(IOException.class);
    }

    @Test
    void testALocationOutsideTheStoresPrefixIsRejected() {
        for (final String key : List.of("", "otherStore/abc/000000000001/", KEY_PREFIX)) {
            final FileStoreLocation location = new FileStoreLocation(STORE_NAME, "s3://" + BUCKET + "/" + key);
            assertThatThrownBy(() -> s3FileStore.resolve(location)).as(key).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> s3FileStore.delete(location)).as(key).isInstanceOf(IOException.class);
        }
        final FileStoreLocation otherBucket = FileStoreLocation.s3(
                STORE_NAME, "other-bucket", KEY_PREFIX + "x/000000000001/");
        assertThatThrownBy(() -> s3FileStore.resolve(otherBucket)).isInstanceOf(IOException.class);
    }

    @Test
    void testGroupsLiveUnderAWriterPrefixFreshPerInstance() throws IOException {
        final FileStoreLocation first = commit(s3FileStore, "1");
        final S3FileStore another = new S3FileStore(
                STORE_NAME, BUCKET, KEY_PREFIX, stubS3Client, tempDir.resolve("other-root"));
        final FileStoreLocation other = commit(another, "1");

        assertThat(first.getS3Key()).startsWith(KEY_PREFIX).endsWith("/000000000001/");
        assertThat(other.getS3Key()).startsWith(KEY_PREFIX).endsWith("/000000000001/");
        assertThat(first).isNotEqualTo(other);
        // Any instance resolves any instance's group.
        assertThat(another.resolve(first).resolve("proxy.zip")).hasContent("1");
    }

    @Test
    void testResolveReportsAbsenceRatherThanReturningAnEmptyDirectory() throws IOException {
        final FileStoreLocation location = commit(s3FileStore, "zip-data");
        s3FileStore.delete(location);

        assertThatThrownBy(() -> s3FileStore.resolve(location))
                .isInstanceOf(FileGroupNotFoundException.class)
                .hasMessageContaining("already been consumed");
    }

    /**
     * A group is several PUTs, and any subset of them can survive a crash. Only the marker, PUT
     * last, proves the group whole; objects without it are an interrupted upload whoever wrote them.
     */
    @Test
    void testObjectsWithoutTheMarkerDoNotResolve() throws IOException {
        final FileStoreLocation location = commit(s3FileStore, "zip-data");

        assertThat(stubS3Client.removeObjectsEndingWith(S3FileStore.COMPLETE_MARKER_NAME))
                .as("commit must have written a marker for there to be one to remove")
                .isEqualTo(1);

        assertThatThrownBy(() -> s3FileStore.resolve(location))
                .isInstanceOf(FileGroupNotFoundException.class)
                .hasMessageContaining("the upload did not finish");
    }

    @Test
    void testOnlyTheMarkerAtTheGroupsOwnPrefixCounts() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            Files.createDirectories(write.getPath().resolve("part"));
            Files.writeString(write.getPath().resolve("part").resolve(S3FileStore.COMPLETE_MARKER_NAME), "");
            Files.writeString(write.getPath().resolve("part").resolve("proxy.zip"), "p");
            location = write.commit();
        }
        assertThat(s3FileStore.resolve(location).resolve("part").resolve("proxy.zip")).hasContent("p");

        // Remove the real marker, leaving the same-named file inside the part.
        stubS3Client.removeObjectsEndingWith("/" + S3FileStore.COMPLETE_MARKER_NAME);
        stubS3Client.putObjectDirectly(
                BUCKET + "/" + location.getS3Key() + "part/" + S3FileStore.COMPLETE_MARKER_NAME, "");

        assertThatThrownBy(() -> s3FileStore.resolve(location)).isInstanceOf(FileGroupNotFoundException.class);
    }

    @Test
    void testTheMarkerIsWrittenLast() throws IOException {
        commit(s3FileStore, "zip-data");
        final List<String> putOrder = stubS3Client.getPutOrder();

        assertThat(putOrder).hasSize(2);
        assertThat(putOrder.getLast()).endsWith(S3FileStore.COMPLETE_MARKER_NAME);
    }

    @Test
    void testDeleteRemovesTheMarkerBeforeTheData() throws IOException {
        final FileStoreLocation location = commitMetaAndZip(s3FileStore);

        s3FileStore.delete(location);

        final List<String> deletionOrder = stubS3Client.getDeletionOrder();
        assertThat(deletionOrder).hasSize(3);
        assertThat(deletionOrder.getFirst()).endsWith(S3FileStore.COMPLETE_MARKER_NAME);
        assertThat(stubS3Client.objectCount()).isZero();
    }

    /**
     * Each resolve is a fresh download the caller owns; consuming one must not disturb another.
     */
    @Test
    void testEachResolveGetsItsOwnDirectory() throws IOException {
        final FileStoreLocation location = commit(s3FileStore, "zip-data");

        final Path first = s3FileStore.resolve(location);
        final Path second = s3FileStore.resolve(location);

        assertThat(first).isNotEqualTo(second);
        assertThat(first.resolve("proxy.zip")).hasContent("zip-data");
        FileUtil.deleteDir(first);
        assertThat(second.resolve("proxy.zip")).hasContent("zip-data");
    }

    @Test
    void testTheMarkerIsNotMaterialisedForTheCaller() throws IOException {
        final FileStoreLocation location = commit(s3FileStore, "zip-data");

        try (final Stream<Path> contents = Files.list(s3FileStore.resolve(location))) {
            assertThat(contents.map(p -> p.getFileName().toString())).containsExactly("proxy.zip");
        }
    }

    @Test
    void testANestedGroupIsUploadedAndDownloadedWithItsTree() throws IOException {
        final FileStoreLocation location;
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            Files.createDirectories(write.getPath().resolve("a"));
            Files.createDirectories(write.getPath().resolve("b"));
            Files.writeString(write.getPath().resolve("a").resolve("proxy.zip"), "a");
            Files.writeString(write.getPath().resolve("b").resolve("proxy.zip"), "b");
            location = write.commit();
        }

        assertThat(stubS3Client.getPutOrder())
                .contains(location.getS3Key() + "a/proxy.zip", location.getS3Key() + "b/proxy.zip");
        final Path resolved = s3FileStore.resolve(location);
        assertThat(resolved.resolve("a").resolve("proxy.zip")).hasContent("a");
        assertThat(resolved.resolve("b").resolve("proxy.zip")).hasContent("b");
    }

    @Test
    void testLocalScratchIsClearedAtStartUp() throws IOException {
        final Path abandoned = s3FileStore.newWrite().getPath();
        Files.writeString(abandoned.resolve("proxy.zip"), "never-committed");
        final Path resolved = s3FileStore.resolve(commit(s3FileStore, "x"));
        assertThat(abandoned).exists();
        assertThat(resolved).exists();

        new S3FileStore(STORE_NAME, BUCKET, KEY_PREFIX, stubS3Client, tempDir.resolve("local-root"));

        assertThat(abandoned).doesNotExist();
        assertThat(resolved).doesNotExist();
    }

    /**
     * The upload is acknowledged, so the commit has happened; a failure to remove local staging is
     * disk left behind, not a failed commit.
     */
    @Test
    void testCommitSucceedsWhenLocalStagingCannotBeDeleted() throws Exception {
        final FileStoreLocation location;
        try (final FileStoreWrite write = s3FileStore.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "Feed:X");
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip-data");

            final Optional<UndeletableDir> lock = UndeletableDir.lock(write.getPath());
            Assumptions.assumeThat(lock).as("this environment can make a delete fail").isPresent();
            try (final UndeletableDir undeletable = lock.get()) {
                location = write.commit();
                assertThat(write.getPath()).exists();
            }
        }

        final Path resolved = s3FileStore.resolve(location);
        assertThat(resolved.resolve("proxy.meta")).hasContent("Feed:X");
        assertThat(resolved.resolve("proxy.zip")).hasContent("zip-data");
    }

    /**
     * S3 caps a listing page at 1000 keys and the truncation is invisible unless the continuation
     * token is followed. With a page of one, a three-object group is only whole if every page is read.
     */
    @Test
    void testListingsFollowContinuationTokens() throws IOException {
        final FileStoreLocation location = commitMetaAndZip(s3FileStore);
        stubS3Client.setPageSize(1);

        final Path resolved = s3FileStore.resolve(location);
        assertThat(resolved.resolve("proxy.meta")).hasContent("Feed:X");
        assertThat(resolved.resolve("proxy.zip")).hasContent("zip-data");

        s3FileStore.delete(location);
        assertThat(stubS3Client.objectCount()).as("every page was deleted").isZero();
    }

    @Test
    void testCloseReleasesTheClient() {
        s3FileStore.close();
        assertThat(stubS3Client.isClosed()).isTrue();
    }

    private static FileStoreLocation commit(final S3FileStore store, final String content) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), content);
            return write.commit();
        }
    }

    private static FileStoreLocation commitMetaAndZip(final S3FileStore store) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.meta"), "Feed:X");
            Files.writeString(write.getPath().resolve("proxy.zip"), "zip-data");
            return write.commit();
        }
    }
}
