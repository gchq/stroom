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

package stroom.proxy.app.pipeline.store.filesystem;

import stroom.proxy.app.handler.DirUtil;
import stroom.proxy.app.handler.Durability;
import stroom.proxy.app.pipeline.UndeletableDir;
import stroom.proxy.app.pipeline.store.FileGroupNotFoundException;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreType;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.test.common.util.test.StroomUnitTest;

import org.assertj.core.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What {@link FilesystemFileStore} does beyond the {@link stroom.proxy.app.pipeline.store.FileStore}
 * contract: the numbered layout, the two writer-root schemes, the two cleanup mechanisms, and the
 * lock-free cascade.
 */
class TestFilesystemFileStore extends StroomUnitTest {

    private static final String STORE_NAME = "store";
    private static final Duration ORPHAN_AGE = Duration.ofDays(1);

    // ------------------------------------------------------------------
    // Layout and ids
    // ------------------------------------------------------------------

    @Test
    void testGroupsAreNumberedSequentiallyInTheNestedLayoutUnderTheRoot() throws IOException {
        final FilesystemFileStore store = localStore();

        final Path first = store.resolve(commit(store, "1"));
        final Path second = store.resolve(commit(store, "2"));

        assertThat(DirUtil.pathToId(first)).isEqualTo(1L);
        assertThat(DirUtil.pathToId(second)).isEqualTo(2L);
        assertThat(first).isEqualTo(DirUtil.createPath(store.getRoot(), 1));
        assertThat(store.getWriterRoot()).as("local mode: the writer root is the root").isEqualTo(store.getRoot());
    }

    @Test
    void testALocalStoreContinuesItsNumberingAcrossARestart() throws IOException {
        final Path root = getCurrentTestDir().resolve(STORE_NAME);
        final FilesystemFileStore first = new FilesystemFileStore(STORE_NAME, root);
        commit(first, "1");
        commit(first, "2");

        final FilesystemFileStore restarted = new FilesystemFileStore(STORE_NAME, root);

        assertThat(DirUtil.pathToId(restarted.resolve(commit(restarted, "3")))).isEqualTo(3L);
    }

    @Test
    void testASharedStoreGetsAFreshWriterRootPerInstanceAndStartsAtOne() throws IOException {
        final Path root = getCurrentTestDir().resolve(STORE_NAME);
        final FilesystemFileStore nodeA = sharedStore(root);
        final FilesystemFileStore nodeB = sharedStore(root);

        assertThat(nodeA.getWriterRoot()).isNotEqualTo(nodeB.getWriterRoot());
        assertThat(nodeA.getWriterRoot().getParent()).isEqualTo(root);
        assertThat(nodeB.getWriterRoot().getParent()).isEqualTo(root);

        final Path a1 = nodeA.resolve(commit(nodeA, "a"));
        final Path b1 = nodeB.resolve(commit(nodeB, "b"));
        assertThat(DirUtil.pathToId(a1)).isEqualTo(1L);
        assertThat(DirUtil.pathToId(b1)).isEqualTo(1L);
        assertThat(a1).isNotEqualTo(b1);

        // Any node resolves any node's group: the location is complete.
        assertThat(nodeB.resolve(FileStoreLocation.filesystem(STORE_NAME, a1)).resolve("proxy.zip")).hasContent("a");
    }

    @Test
    void testAStablePathOnlyEverAppearsThroughCommit() throws IOException {
        final FilesystemFileStore store = localStore();
        final FileStoreWrite write = store.newWrite();
        Files.writeString(write.getPath().resolve("proxy.zip"), "staged");

        assertThat(write.getPath()).startsWith(store.getWriterRoot().resolve(FilesystemFileStore.STAGING_DIR_NAME));
        assertThat(DirUtil.getMaxDirId(store.getRoot())).as("nothing published yet").isZero();

        final FileStoreLocation location = write.commit();
        assertThat(write.getPath()).doesNotExist();
        assertThat(DirUtil.getMaxDirId(store.getRoot())).isEqualTo(1L);
        assertThat(store.resolve(location).resolve("proxy.zip")).hasContent("staged");
    }

    @Test
    void testCommitProducesAResolvableGroupUnderEveryDurabilityMode() throws IOException {
        for (final Durability durability : Durability.values()) {
            final FilesystemFileStore store = new FilesystemFileStore(
                    STORE_NAME, getCurrentTestDir().resolve(STORE_NAME + "-" + durability),
                    FileStoreType.LOCAL_FILESYSTEM, durability, null);
            assertThat(store.resolve(commit(store, "zip-" + durability)).resolve("proxy.zip"))
                    .as("a commit must be resolvable under %s", durability)
                    .hasContent("zip-" + durability);
        }
    }

    // ------------------------------------------------------------------
    // Resolve and delete
    // ------------------------------------------------------------------

    @Test
    void testResolveReportsAbsenceOnceTheGroupIsDeleted() throws IOException {
        final FilesystemFileStore store = localStore();
        final FileStoreLocation location = commit(store, "x");
        store.delete(location);

        assertThatThrownBy(() -> store.resolve(location))
                .isInstanceOf(FileGroupNotFoundException.class)
                .hasMessageContaining("already been consumed");
    }

    @Test
    void testOnlyAGroupPathIsAccepted() throws IOException {
        final FilesystemFileStore store = localStore();
        final Path group = store.resolve(commit(store, "x"));

        for (final Path notAGroup : List.of(store.getRoot(), group.getParent(), group.resolve("proxy.zip"),
                getCurrentTestDir().resolve("elsewhere"))) {
            final FileStoreLocation location = FileStoreLocation.filesystem(STORE_NAME, notAGroup);
            assertThatThrownBy(() -> store.resolve(location))
                    .as("resolve %s", notAGroup).isInstanceOf(IOException.class);
            assertThatThrownBy(() -> store.delete(location)).as("delete %s", notAGroup).isInstanceOf(IOException.class);
        }
        assertThat(store.getRoot()).exists();
        assertThat(group).exists();
    }

    /**
     * The delete is the operation the caller asked for, so a failure is a throw rather than a
     * logged shrug: claiming success over a group still on disk would be silent.
     */
    @Test
    void testDeleteReportsFailureRatherThanClaimingSuccess() throws Exception {
        final FilesystemFileStore store = localStore();
        final FileStoreLocation location = commit(store, "x");
        final Path group = store.resolve(location);

        final Optional<UndeletableDir> lock = UndeletableDir.lock(group);
        Assumptions.assumeThat(lock).as("this environment can make a delete fail").isPresent();
        try (final UndeletableDir undeletable = lock.get()) {
            assertThatThrownBy(() -> store.delete(location))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Failed to fully delete");
        }
        assertThat(group).exists();
    }

    @Test
    void testDeleteRemovesANonDirectoryAtAGroupPath() throws IOException {
        final FilesystemFileStore store = localStore();
        final Path leaf = DirUtil.createPath(store.getRoot(), 7);
        Files.createDirectories(leaf.getParent());
        Files.writeString(leaf, "not a directory");

        store.delete(FileStoreLocation.filesystem(STORE_NAME, leaf));

        assertThat(leaf).doesNotExist();
    }

    @Test
    void testDeleteCascadesEmptyNumberingDirectoriesButNeverTheWriterRoot() throws IOException {
        final FilesystemFileStore store = localStore();
        final FileStoreLocation location = commit(store, "x");
        final Path group = store.resolve(location);
        final Path depthDir = store.getRoot().resolve("0");
        assertThat(group.getParent()).isEqualTo(depthDir);

        store.delete(location);

        assertThat(depthDir).as("the emptied depth directory is cascaded").doesNotExist();
        assertThat(store.getRoot()).exists();
        assertThat(store.getWriterRoot().resolve(FilesystemFileStore.STAGING_DIR_NAME)).exists();
    }

    /**
     * The forwarder and the aggregators move the lent directory out before the stage deletes it, so
     * the common case is a delete of something already gone - and it must still tidy the numbering
     * directories, or every thousand groups leaves one behind until the next sweep.
     */
    @Test
    void testDeleteCascadesEvenWhenTheConsumerAlreadyMovedTheGroupOut() throws IOException {
        final FilesystemFileStore store = localStore();
        final FileStoreLocation location = commit(store, "x");
        final Path group = store.resolve(location);
        Files.move(group, getCurrentTestDir().resolve("moved-by-consumer"));
        assertThat(group.getParent()).exists();

        store.delete(location);

        assertThat(group.getParent()).doesNotExist();
        assertThat(store.getRoot()).exists();
    }

    @Test
    void testDeleteCascadeStopsAtANonEmptyDirectory() throws IOException {
        final FilesystemFileStore store = localStore();
        final FileStoreLocation first = commit(store, "1");
        final FileStoreLocation second = commit(store, "2");

        store.delete(first);

        assertThat(store.resolve(second).resolve("proxy.zip")).hasContent("2");
        assertThat(store.getRoot().resolve("0")).exists();
    }

    /**
     * The cascade and a concurrent commit share intermediate directories with no lock between
     * them. Every commit must land and be resolvable while a deleter is cascading the directories
     * around it. This runs the race rather than proving it; the argument is in the design.
     */
    @Test
    void testCommitsSucceedWhileAnotherThreadCascadeDeletesAroundThem() throws Exception {
        final FilesystemFileStore store = localStore();
        final int groups = 400;
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CountDownLatch go = new CountDownLatch(1);
        final List<FileStoreLocation> committed = new ArrayList<>();
        try {
            final Future<?> writer = executor.submit(() -> {
                go.await();
                for (int i = 0; i < groups; i++) {
                    final FileStoreLocation location = commit(store, "g" + i);
                    assertThat(store.resolve(location).resolve("proxy.zip")).hasContent("g" + i);
                    synchronized (committed) {
                        committed.add(location);
                    }
                }
                return null;
            });
            final Future<?> deleter = executor.submit(() -> {
                go.await();
                int deleted = 0;
                while (deleted < groups) {
                    final FileStoreLocation next;
                    synchronized (committed) {
                        next = committed.size() > deleted
                                ? committed.get(deleted)
                                : null;
                    }
                    if (next == null) {
                        Thread.onSpinWait();
                        continue;
                    }
                    store.delete(next);
                    deleted++;
                }
                return null;
            });
            go.countDown();
            writer.get(60, TimeUnit.SECONDS);
            deleter.get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(committed).hasSize(groups);
        for (final FileStoreLocation location : committed) {
            assertThatThrownBy(() -> store.resolve(location)).isInstanceOf(FileGroupNotFoundException.class);
        }
        assertThat(store.getRoot()).exists();
    }

    // ------------------------------------------------------------------
    // Local mode: start-up
    // ------------------------------------------------------------------

    @Test
    void testALocalStoreClearsStagingAtStartUpAndKeepsCommittedGroups() throws IOException {
        final Path root = getCurrentTestDir().resolve(STORE_NAME);
        final FilesystemFileStore first = new FilesystemFileStore(STORE_NAME, root);
        final Path abandoned = first.newWrite().getPath();
        Files.writeString(abandoned.resolve("proxy.zip"), "never-committed");
        final FileStoreLocation committed = commit(first, "committed");

        final FilesystemFileStore restarted = new FilesystemFileStore(STORE_NAME, root);

        assertThat(abandoned).doesNotExist();
        assertThat(restarted.resolve(committed).resolve("proxy.zip")).hasContent("committed");
    }

    @Test
    void testDeleteAllExceptRemovesUnreferencedGroupsEmptyDirectoriesAndReseedsTheCounter() throws IOException {
        final FilesystemFileStore store = localStore();
        final FileStoreLocation orphan1 = commit(store, "orphan-1");
        final FileStoreLocation live = commit(store, "live");
        final FileStoreLocation orphan2 = commit(store, "orphan-2");
        final FileStoreLocation otherStoresGroup = FileStoreLocation.filesystem("otherStore", store.resolve(orphan2));

        final int deleted = store.deleteAllExcept(Set.of(live, otherStoresGroup));

        assertThat(deleted).isEqualTo(2);
        assertThatThrownBy(() -> store.resolve(orphan1)).isInstanceOf(FileGroupNotFoundException.class);
        assertThatThrownBy(() -> store.resolve(orphan2)).isInstanceOf(FileGroupNotFoundException.class);
        assertThat(store.resolve(live).resolve("proxy.zip")).hasContent("live");
        assertThat(DirUtil.pathToId(store.resolve(commit(store, "next"))))
                .as("the counter continues from the highest id left, and id 3 is safe to reuse: nothing names it")
                .isEqualTo(3L);
    }

    @Test
    void testAFullyDrainedLocalStoreRestartsItsNumberingAtOne() throws IOException {
        final FilesystemFileStore store = localStore();
        commit(store, "1");
        commit(store, "2");

        store.deleteAllExcept(Set.of());

        assertThat(store.getRoot().resolve("0")).as("empty numbering directories go too").doesNotExist();
        assertThat(DirUtil.pathToId(store.resolve(commit(store, "again")))).isEqualTo(1L);
    }

    @Test
    void testASharedStoreIsNeverSweptAgainstTheQueues() {
        final FilesystemFileStore store = sharedStore(getCurrentTestDir().resolve(STORE_NAME));
        assertThatThrownBy(() -> store.deleteAllExcept(Set.of())).isInstanceOf(IllegalStateException.class);
    }

    // ------------------------------------------------------------------
    // Shared mode: age-based sweep
    // ------------------------------------------------------------------

    @Test
    void testASharedStoreLeavesEverythingSharedAloneAtStartUp() throws IOException {
        final Path root = getCurrentTestDir().resolve(STORE_NAME);
        final FilesystemFileStore other = sharedStore(root);
        final Path othersStaging = other.newWrite().getPath();
        Files.writeString(othersStaging.resolve("proxy.zip"), "in progress on another node");
        final FileStoreLocation othersGroup = commit(other, "committed on another node");

        final FilesystemFileStore thisNode = sharedStore(root);

        assertThat(othersStaging).as("another node's write in progress is live").exists();
        assertThat(thisNode.resolve(othersGroup).resolve("proxy.zip")).hasContent("committed on another node");
    }

    @Test
    void testSweepDeletesOnlyWhatIsOlderThanOrphanAge() throws IOException {
        final Path root = getCurrentTestDir().resolve(STORE_NAME);
        final FilesystemFileStore deadNode = sharedStore(root);
        final Path oldStaging = deadNode.newWrite().getPath();
        Files.writeString(oldStaging.resolve("proxy.zip"), "stale");
        final FileStoreLocation oldGroup = commit(deadNode, "old orphan");
        final FileStoreLocation youngGroup = commit(deadNode, "young");
        age(oldStaging);
        age(deadNode.resolve(oldGroup));

        final FilesystemFileStore thisNode = sharedStore(root);
        final Path ownStaging = thisNode.newWrite().getPath();
        Files.writeString(ownStaging.resolve("proxy.zip"), "in progress here");
        final FileStoreLocation ownGroup = commit(thisNode, "own");

        final int deleted = thisNode.sweep();

        assertThat(deleted).isEqualTo(2);
        // The sweep cannot tell the orphan from live work that waited too long, so the group count
        // is the one signal an operator has that orphanAge is too short; staging is counted apart
        // because deleting it is always safe.
        assertThat(thisNode.getSweptGroupCount()).isEqualTo(1);
        assertThat(thisNode.getSweptStagingCount()).isEqualTo(1);
        assertThat(oldStaging).doesNotExist();
        assertThatThrownBy(() -> thisNode.resolve(oldGroup)).isInstanceOf(FileGroupNotFoundException.class);
        assertThat(thisNode.resolve(youngGroup).resolve("proxy.zip")).hasContent("young");
        assertThat(ownStaging).exists();
        assertThat(thisNode.resolve(ownGroup).resolve("proxy.zip")).hasContent("own");
        assertThat(deadNode.getWriterRoot()).as("still holds a young group").exists();
    }

    @Test
    void testSweepRemovesADeadNodesWriterRootOnceItIsEmptyAndOld() throws IOException {
        final Path root = getCurrentTestDir().resolve(STORE_NAME);
        final FilesystemFileStore deadNode = sharedStore(root);
        final FileStoreLocation group = commit(deadNode, "x");
        deadNode.delete(group);
        ageTree(deadNode.getWriterRoot());

        final FilesystemFileStore thisNode = sharedStore(root);
        thisNode.sweep();

        assertThat(deadNode.getWriterRoot()).doesNotExist();
        assertThat(thisNode.getWriterRoot()).exists();
    }

    @Test
    void testALocalStoreIsNeverSweptByAge() {
        assertThatThrownBy(() -> localStore().sweep()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testASharedStoreRequiresAPositiveOrphanAge() {
        final Path root = getCurrentTestDir().resolve(STORE_NAME);
        assertThatThrownBy(() -> new FilesystemFileStore(
                STORE_NAME, root, FileStoreType.SHARED_FILESYSTEM, Durability.FILESYSTEM, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FilesystemFileStore(
                STORE_NAME, root, FileStoreType.SHARED_FILESYSTEM, Durability.FILESYSTEM, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------

    private FilesystemFileStore localStore() {
        return new FilesystemFileStore(STORE_NAME, getCurrentTestDir().resolve(STORE_NAME));
    }

    private static FilesystemFileStore sharedStore(final Path root) {
        return new FilesystemFileStore(
                STORE_NAME, root, FileStoreType.SHARED_FILESYSTEM, Durability.FILESYSTEM, ORPHAN_AGE);
    }

    private static FileStoreLocation commit(final FilesystemFileStore store, final String content) throws IOException {
        try (final FileStoreWrite write = store.newWrite()) {
            Files.writeString(write.getPath().resolve("proxy.zip"), content);
            return write.commit();
        }
    }

    private static void age(final Path path) throws IOException {
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minus(ORPHAN_AGE.multipliedBy(2))));
    }

    private static void ageTree(final Path root) throws IOException {
        try (final Stream<Path> walk = Files.walk(root)) {
            for (final Path path : walk.toList()) {
                age(path);
            }
        }
    }
}
