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

package stroom.contentindex;

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.lock.mock.MockClusterLockService;
import stroom.contentindex.ContentIndexConfig.StorageType;
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndexable;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.DocContentMatch;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.StringMatch;
import stroom.node.api.NodeInfo;
import stroom.security.mock.MockSecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.shared.ThreadPool;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.time.StroomDuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/// Unit tests for the locking in [LuceneContentIndex]. Searching and highlighting are tested by
/// the integration test of the same name in stroom-app.
@ExtendWith(MockitoExtension.class)
class TestLuceneContentIndex {

    private static final String INDEXABLE_TYPE = "TestDoc";
    private static final DocRef DOC_REF = new DocRef(INDEXABLE_TYPE, "c5b1a7e0-2f4e-4d8a-9a51-0d6d3c3f7b11", "Doc 1");
    // Long enough for a busy machine, far shorter than a test that hangs on the re-build
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);
    private static final Duration INITIALISATION_TIMEOUT = Duration.ofSeconds(60);

    @TempDir
    private Path tempDir;

    @Mock
    private ExplorerNodeService mockExplorerNodeService;
    @Mock
    private NodeInfo mockNodeInfo;
    @Mock
    private ClusterNodeManager mockClusterNodeManager;

    private ExecutorService executorService;
    private BlockingContentIndexable indexable;

    @BeforeEach
    void setUp() {
        executorService = Executors.newCachedThreadPool();
        indexable = new BlockingContentIndexable(DOC_REF, "apple");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        // Let any re-build we held up go on, so its thread is not left waiting on the latch
        indexable.release();
        executorService.shutdownNow();
        // Wait for every thread to stop before JUnit deletes the temp dir. A re-build that is
        // still writing index files would make that delete fail.
        assertThat(executorService.awaitTermination(MAX_WAIT.toMillis(), TimeUnit.MILLISECONDS))
                .isTrue();
    }

    @Test
    void testOnChange_nonIndexableTypeDuringRebuild() {
        final LuceneContentIndex contentIndex = startWithRebuildInProgress();

        // Stroom fires this on every boot, on the main thread
        final EntityEvent nodeEvent = new EntityEvent(
                new DocRef("Node", "1", "node1a"),
                EntityAction.UPDATE);

        final CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> contentIndex.onChange(nodeEvent), executorService);

        assertThat(future)
                .succeedsWithin(MAX_WAIT);
    }

    @Test
    void testOnChange_indexableTypeDuringRebuild() {
        final LuceneContentIndex contentIndex = startWithRebuildInProgress();

        indexable.setData("banana");
        final EntityEvent docEvent = new EntityEvent(DOC_REF, EntityAction.UPDATE);

        final CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> contentIndex.onChange(docEvent), executorService);

        assertThat(future)
                .succeedsWithin(MAX_WAIT);

        // The re-build read the old data before the change, so only the queued event can index
        // the new data, once the re-build is done
        indexable.release();
        assertThat(awaitMatchCount(contentIndex, "banana", 1))
                .isEqualTo(1);
    }

    @Test
    void testFindInContent_duringRebuild() {
        final LuceneContentIndex contentIndex = startWithRebuildInProgress();

        // Searches use the index already on disk while the re-build runs
        final CompletableFuture<ResultPage<DocContentMatch>> future = CompletableFuture.supplyAsync(
                () -> contentIndex.findInContent(createRequest("apple")), executorService);

        assertThat(future)
                .succeedsWithin(MAX_WAIT)
                .satisfies(resultPage ->
                        assertThat(resultPage.getValues())
                                .extracting(DocContentMatch::getDocRef)
                                .containsExactly(DOC_REF));
    }

    @Test
    void testOnChange_nullEvent() {
        final LuceneContentIndex contentIndex = createContentIndex();

        final CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> contentIndex.onChange(null), executorService);

        assertThat(future)
                .succeedsWithin(MAX_WAIT);
    }

    /// Builds an index on disk, then starts a new content index on it, as on a node restart. The new
    /// one re-builds the index in the background, and this returns with that re-build held part way
    /// through, so it holds the lock on the index files.
    private LuceneContentIndex startWithRebuildInProgress() {
        final LuceneContentIndex firstBoot = createContentIndex();
        try {
            assertThat(firstBoot.awaitInitialisation(INITIALISATION_TIMEOUT))
                    .isTrue();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        indexable.blockNextRead();
        final LuceneContentIndex restarted = createContentIndex();
        assertThat(indexable.awaitReadBlocked(INITIALISATION_TIMEOUT))
                .isTrue();
        return restarted;
    }

    private LuceneContentIndex createContentIndex() {
        final TempDirProvider tempDirProvider = () -> tempDir.resolve("temp");
        // A zero minimum age means every boot re-builds the index
        final ContentIndexConfig config = new ContentIndexConfig(
                "content_index", StorageType.TEMP, StroomDuration.ZERO);
        return new LuceneContentIndex(
                tempDirProvider,
                new SimplePathCreator(() -> tempDir.resolve("home"), tempDirProvider),
                () -> config,
                Set.of(indexable),
                new MockSecurityContext(),
                new SimpleTaskContextFactory(),
                mockExplorerNodeService,
                new FixedExecutorProvider(executorService),
                new MockClusterLockService(),
                mockNodeInfo,
                mockClusterNodeManager);
    }

    private static FindInContentRequest createRequest(final String text) {
        return new FindInContentRequest(new PageRequest(0, 100), null, StringMatch.contains(text));
    }

    private static int awaitMatchCount(final LuceneContentIndex contentIndex,
                                       final String text,
                                       final int expectedCount) {
        final Instant deadline = Instant.now().plus(INITIALISATION_TIMEOUT);
        int count = contentIndex.findInContent(createRequest(text)).size();
        while (count != expectedCount && Instant.now().isBefore(deadline)) {
            ThreadUtil.sleepIgnoringInterrupts(100);
            count = contentIndex.findInContent(createRequest(text)).size();
        }
        return count;
    }


    // --------------------------------------------------------------------------------


    /// A single document whose content read can be held up, to keep a re-build running.
    private static final class BlockingContentIndexable implements ContentIndexable {

        private final DocRef docRef;
        private final AtomicBoolean isNextReadBlocked = new AtomicBoolean(false);
        private final CountDownLatch readBlockedLatch = new CountDownLatch(1);
        private final CountDownLatch releaseLatch = new CountDownLatch(1);
        private volatile String data;

        private BlockingContentIndexable(final DocRef docRef, final String data) {
            this.docRef = docRef;
            this.data = data;
        }

        @Override
        public Map<String, String> getIndexableData(final DocRef docRef) {
            // Read the data before blocking, so a change made while blocked is not seen by this read
            final String currentData = data;
            if (isNextReadBlocked.compareAndSet(true, false)) {
                readBlockedLatch.countDown();
                try {
                    releaseLatch.await();
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return Map.of("txt", currentData);
        }

        @Override
        public Set<DocRef> listDocuments() {
            return Set.of(docRef);
        }

        @Override
        public String getType() {
            return docRef.getType();
        }

        private void setData(final String data) {
            this.data = data;
        }

        private void blockNextRead() {
            isNextReadBlocked.set(true);
        }

        private boolean awaitReadBlocked(final Duration timeout) {
            try {
                return readBlockedLatch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        private void release() {
            releaseLatch.countDown();
        }
    }


    // --------------------------------------------------------------------------------


    private record FixedExecutorProvider(Executor executor) implements ExecutorProvider {

        @Override
        public Executor get() {
            return executor;
        }

        @Override
        public Executor get(final ThreadPool threadPool) {
            return executor;
        }
    }
}
