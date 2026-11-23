/*
 * Copyright 2016 Crown Copyright
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

package stroom.index.lucene553;

import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexDocument;
import stroom.index.impl.IndexShardCommitListener;
import stroom.index.impl.IndexShardUtil;
import stroom.index.impl.IndexShardWriter;
import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeType;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.LuceneIndexField;
import stroom.index.shared.LuceneVersionUtil;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValString;
import stroom.search.extraction.FieldValue;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;

import org.apache.lucene553.document.Document;
import org.apache.lucene553.document.Field.Store;
import org.apache.lucene553.document.StringField;
import org.apache.lucene553.index.DirectoryReader;
import org.apache.lucene553.index.IndexWriter;
import org.apache.lucene553.index.IndexWriterConfig;
import org.apache.lucene553.search.IndexSearcher;
import org.apache.lucene553.search.SearcherManager;
import org.apache.lucene553.store.Directory;
import org.apache.lucene553.store.NIOFSDirectory;
import org.apache.lucene553.store.NoLockFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestIndexShardIO extends StroomUnitTest {

    private static final int MAX_DOCS = 1000000000;

    private PathCreator pathCreator;

    static void main() {
        for (final Object s : System.getProperties().keySet()) {
            System.out.println(s + "=" + System.getProperty((String) s));
        }
    }

    private IndexDocument buildDocument(final int id) {
        final IndexDocument d = new IndexDocument();
        d.add(new FieldValue(LuceneIndexField.createIdField("Id"), ValInteger.create(id)));
        d.add(new FieldValue(LuceneIndexField.createField("Test"), ValString.create("Test")));
        d.add(new FieldValue(LuceneIndexField.createIdField("Id2"), ValInteger.create(id)));

        return d;
    }

    @BeforeEach
    void setUp(@TempDir final Path tempDir) {
        pathCreator = new SimplePathCreator(
                () -> tempDir.resolve("home"),
                () -> tempDir);
    }

    @Test
    void testLeaseHeld_documentsAreCommitted() throws IOException {
        final IndexShard indexShard = createShardOn(VolumeType.SHARED);
        final IndexShardWriter writer = createWriter(indexShard);
        writer.setLeaseCheck(() -> true);

        writer.addDocument(buildDocument(1));
        writer.flush();
        writer.addDocument(buildDocument(2));
        writer.close();

        assertThat(countCommittedDocuments(indexShard)).isEqualTo(2);
    }

    @Test
    void testLeaseLost_addingIsRefused() throws IOException {
        final AtomicBoolean leaseHeld = new AtomicBoolean(true);
        final IndexShard indexShard = createShardOn(VolumeType.SHARED);
        final IndexShardWriter writer = createWriter(indexShard);
        writer.setLeaseCheck(leaseHeld::get);
        writer.addDocument(buildDocument(1));

        leaseHeld.set(false);

        // An IndexException, which is what makes the caller stop using this shard and choose another.
        assertThatThrownBy(() -> writer.addDocument(buildDocument(2)))
                .isInstanceOf(IndexException.class)
                .hasMessageContaining("lease");
        assertThat(writer.getDocumentCount()).isEqualTo(1);
        writer.close();
    }

    @Test
    void testLeaseLostBeforeCommit_uncommittedDocumentsAreDiscarded() throws IOException {
        final AtomicBoolean leaseHeld = new AtomicBoolean(true);
        final IndexShard indexShard = createShardOn(VolumeType.SHARED);
        final IndexShardWriter writer = createWriter(indexShard);
        writer.setLeaseCheck(leaseHeld::get);
        writer.addDocument(buildDocument(1));
        writer.flush();
        writer.addDocument(buildDocument(2));
        writer.addDocument(buildDocument(3));

        // Another node may be writing to the shard by now, so this commit must not happen.
        leaseHeld.set(false);
        writer.flush();

        assertThat(countCommittedDocuments(indexShard)).isEqualTo(1);
        // The writer's count goes back to what is really in the shard.
        assertThat(writer.getDocumentCount()).isEqualTo(1);

        // The writer is finished with. Getting the lease back does not revive it.
        leaseHeld.set(true);
        assertThatThrownBy(() -> writer.addDocument(buildDocument(4)))
                .isInstanceOf(IndexException.class);
        writer.flush();
        writer.close();
        assertThat(countCommittedDocuments(indexShard)).isEqualTo(1);
    }

    @Test
    void testLeaseLostBeforeClose_uncommittedDocumentsAreDiscarded() throws IOException {
        final AtomicBoolean leaseHeld = new AtomicBoolean(true);
        final IndexShard indexShard = createShardOn(VolumeType.SHARED);
        final IndexShardWriter writer = createWriter(indexShard);
        writer.setLeaseCheck(leaseHeld::get);
        writer.addDocument(buildDocument(1));
        writer.flush();
        writer.addDocument(buildDocument(2));

        // Closing normally commits what is outstanding.
        leaseHeld.set(false);
        writer.close();

        assertThat(countCommittedDocuments(indexShard)).isEqualTo(1);
        assertThat(writer.getDocumentCount()).isEqualTo(1);

        // The shard is intact and another writer can carry on with it.
        final IndexShardWriter nextWriter = createWriter(indexShard);
        nextWriter.addDocument(buildDocument(5));
        nextWriter.close();
        assertThat(countCommittedDocuments(indexShard)).isEqualTo(2);
    }

    @Test
    void testLeaseLost_shardWrittenToByAnotherNodeMeanwhile_itsFilesAreLeftAlone() throws IOException {
        final AtomicBoolean leaseHeld = new AtomicBoolean(true);
        final IndexShard indexShard = createShardOn(VolumeType.SHARED);
        final IndexShardWriter writer = createWriter(indexShard);
        writer.setLeaseCheck(leaseHeld::get);
        writer.addDocument(buildDocument(1));
        writer.flush();
        writer.addDocument(buildDocument(2));

        // This node stops renewing its lease for so long that the shard is taken from it and written to by
        // another node. That node is played by a writer that does not see this one's lock, as a writer in
        // another process would not.
        leaseHeld.set(false);
        final Path dir = IndexShardUtil.getIndexPath(indexShard, pathCreator);
        try (final Directory directory = new NIOFSDirectory(dir, NoLockFactory.INSTANCE);
                final IndexWriter otherNode = new IndexWriter(directory, new IndexWriterConfig(null))) {
            for (int i = 0; i < 3; i++) {
                final Document document = new Document();
                document.add(new StringField("Other", "node", Store.NO));
                otherNode.addDocument(document);
            }
            otherNode.commit();
        }

        // This node comes back and closes its writer, which must not touch the files in the shard as
        // it no longer knows which of them are in use.
        writer.close();

        // The document committed here and the three committed by the other node.
        assertThat(countCommittedDocuments(indexShard)).isEqualTo(4);
        final IndexShardWriter nextWriter = createWriter(indexShard);
        nextWriter.addDocument(buildDocument(5));
        nextWriter.close();
        assertThat(countCommittedDocuments(indexShard)).isEqualTo(5);
    }

    @Test
    void testNoLeaseCheck_alwaysWrites() throws IOException {
        // As for a shard on a local volume, which has no lease.
        final IndexShard indexShard = createShardOn(VolumeType.LOCAL);
        final IndexShardWriter writer = createWriter(indexShard);

        writer.addDocument(buildDocument(1));
        writer.close();

        assertThat(countCommittedDocuments(indexShard)).isEqualTo(1);
        assertThat(writer.hasReplacedCommits()).isFalse();
    }

    @Test
    void testPruneReplacedCommits() throws IOException {
        final IndexShard indexShard = createShardOn(VolumeType.SHARED);
        final Path dir = IndexShardUtil.getIndexPath(indexShard, pathCreator);
        final IndexShardWriter writer = createWriter(indexShard);
        writer.addDocument(buildDocument(1));
        writer.flush();
        writer.addDocument(buildDocument(2));
        writer.close();

        // The writer closed while still keeping the commit it had replaced.
        assertThat(writer.hasReplacedCommits()).isTrue();
        assertThat(countCommits(dir)).isEqualTo(2);

        // Much later a writer is opened just to remove it.
        final IndexShardWriter pruningWriter = createWriter(indexShard);
        pruningWriter.pruneReplacedCommits();
        pruningWriter.close();

        assertThat(pruningWriter.hasReplacedCommits()).isFalse();
        assertThat(countCommits(dir)).isEqualTo(1);
        assertThat(countCommittedDocuments(indexShard)).isEqualTo(2);
    }

    @Test
    void testCommitListener_toldBeforeAndAfterEachCommit() throws IOException {
        final IndexShard indexShard = createShardOn(VolumeType.LOCAL);
        final IndexShardWriter writer = createWriter(indexShard);
        final RecordingCommitListener listener = new RecordingCommitListener();
        writer.setCommitListener(listener);

        writer.addDocument(buildDocument(1));
        listener.writtenRowIds = List.of(10L, 11L);
        writer.flush();

        // Told which rows it found before the commit, not any that were written while it ran.
        assertThat(listener.events).containsExactly("before", "after [10, 11]");
        assertThat(writer.needsPendingStreamRecovery()).isFalse();

        // Closing commits too.
        listener.events.clear();
        listener.writtenRowIds = List.of(12L);
        writer.addDocument(buildDocument(2));
        writer.close();

        assertThat(listener.events).containsExactly("before", "after [12]");
        assertThat(writer.needsPendingStreamRecovery()).isFalse();
        assertThat(countCommittedDocuments(indexShard)).isEqualTo(2);
    }

    @Test
    void testCommitListener_leaseLost_nothingIsSaidToBeCommitted() throws IOException {
        final AtomicBoolean leaseHeld = new AtomicBoolean(true);
        final IndexShard indexShard = createShardOn(VolumeType.SHARED);
        final IndexShardWriter writer = createWriter(indexShard);
        final RecordingCommitListener listener = new RecordingCommitListener();
        writer.setLeaseCheck(leaseHeld::get);
        writer.setCommitListener(listener);
        writer.addDocument(buildDocument(1));

        leaseHeld.set(false);
        writer.flush();
        writer.close();

        // The rows are left as they are, and the shard is marked so that whoever opens it next recovers
        // the streams it may hold part of.
        assertThat(listener.events).containsExactly("before");
        assertThat(writer.needsPendingStreamRecovery()).isTrue();
    }

    @Test
    void testCommitListener_leaseLostBeforeClose() throws IOException {
        final AtomicBoolean leaseHeld = new AtomicBoolean(true);
        final IndexShard indexShard = createShardOn(VolumeType.SHARED);
        final IndexShardWriter writer = createWriter(indexShard);
        final RecordingCommitListener listener = new RecordingCommitListener();
        writer.setLeaseCheck(leaseHeld::get);
        writer.setCommitListener(listener);
        writer.addDocument(buildDocument(1));

        leaseHeld.set(false);
        writer.close();

        assertThat(listener.events).isEmpty();
        assertThat(writer.needsPendingStreamRecovery()).isTrue();
    }

    @Test
    void testCommitListener_failureToRecordDoesNotStopTheCommit() throws IOException {
        final IndexShard indexShard = createShardOn(VolumeType.LOCAL);
        final IndexShardWriter writer = createWriter(indexShard);
        writer.setCommitListener(new IndexShardCommitListener() {
            @Override
            public List<Long> beforeCommit() {
                throw new RuntimeException("no database");
            }

            @Override
            public void afterCommit(final List<Long> rowIds) {
                throw new RuntimeException("no database");
            }

            @Override
            public void commitFailed() {
                throw new RuntimeException("no database");
            }
        });

        writer.addDocument(buildDocument(1));
        writer.flush();
        writer.close();

        assertThat(countCommittedDocuments(indexShard)).isEqualTo(1);
        assertThat(writer.needsPendingStreamRecovery()).isFalse();
    }

    @Test
    void testDeleteStreams() throws IOException {
        final IndexShard indexShard = createShardOn(VolumeType.LOCAL);
        final IndexShardWriter writer = createWriter(indexShard);
        final RecordingCommitListener listener = new RecordingCommitListener();
        writer.setCommitListener(listener);
        for (long streamId = 1; streamId <= 3; streamId++) {
            for (int i = 0; i < 10; i++) {
                writer.addDocument(buildStreamDocument(streamId));
            }
        }
        // Some of the documents to remove are committed and some are not.
        writer.flush();
        for (int i = 0; i < 5; i++) {
            writer.addDocument(buildStreamDocument(2));
            writer.addDocument(buildStreamDocument(3));
        }
        listener.events.clear();

        writer.deleteStreams(Set.of(2L, 99L));

        // The removal is committed, and the count follows what is left.
        assertThat(listener.events).containsExactly("before", "after []");
        assertThat(countCommittedDocuments(indexShard)).isEqualTo(25);
        assertThat(writer.getDocumentCount()).isEqualTo(25);

        // Removing them again changes nothing.
        writer.deleteStreams(Set.of(2L));
        assertThat(countCommittedDocuments(indexShard)).isEqualTo(25);

        writer.deleteStreams(Set.of(1L, 3L));
        writer.close();
        assertThat(countCommittedDocuments(indexShard)).isZero();
    }

    @Test
    void testDeleteStreams_refusedWithoutTheLease() throws IOException {
        final AtomicBoolean leaseHeld = new AtomicBoolean(true);
        final IndexShard indexShard = createShardOn(VolumeType.SHARED);
        final IndexShardWriter writer = createWriter(indexShard);
        writer.setLeaseCheck(leaseHeld::get);
        writer.addDocument(buildStreamDocument(1));
        writer.flush();

        leaseHeld.set(false);

        assertThatThrownBy(() -> writer.deleteStreams(Set.of(1L)))
                .isInstanceOf(IndexException.class);
        writer.close();
        assertThat(countCommittedDocuments(indexShard)).isEqualTo(1);
    }

    @Test
    void testDeleteStreams_refusedOnceClosed() throws IOException {
        final IndexShard indexShard = createShardOn(VolumeType.LOCAL);
        final IndexShardWriter writer = createWriter(indexShard);
        writer.addDocument(buildStreamDocument(1));
        writer.close();

        assertThatThrownBy(() -> writer.deleteStreams(Set.of(1L)))
                .isInstanceOf(IndexException.class);
    }

    private IndexDocument buildStreamDocument(final long streamId) {
        final IndexDocument d = new IndexDocument();
        d.add(new FieldValue(LuceneIndexField.createIdField(IndexConstants.STREAM_ID), ValLong.create(streamId)));
        d.add(new FieldValue(LuceneIndexField.createField("Test"), ValString.create("Test")));
        return d;
    }

    private IndexShard createShardOn(final VolumeType volumeType) throws IOException {
        final IndexVolume volume = IndexVolume
                .builder()
                .path(FileUtil.getCanonicalPath(Files.createTempDirectory("stroom")))
                .volumeType(volumeType)
                .build();
        final LuceneIndexDoc index = LuceneIndexDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test")
                .build();
        return createShard(index, volume);
    }

    private IndexShardWriter createWriter(final IndexShard indexShard) {
        return new Lucene553IndexShardWriter(
                null, new IndexConfig(), indexShard, pathCreator, MAX_DOCS);
    }

    private int countCommittedDocuments(final IndexShard indexShard) throws IOException {
        final Path dir = IndexShardUtil.getIndexPath(indexShard, pathCreator);
        try (final Directory directory = new NIOFSDirectory(dir);
                final DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        }
    }

    private int countCommits(final Path dir) throws IOException {
        try (final Directory directory = new NIOFSDirectory(dir)) {
            return DirectoryReader.listCommits(directory).size();
        }
    }

    @Test
    void testOpenCloseManyWrite() throws IOException {
        final IndexVolume volume = IndexVolume
                .builder()
                .path(FileUtil.getCanonicalPath(Files.createTempDirectory("stroom")))
                .build();
        final LuceneIndexDoc index = LuceneIndexDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test")
                .build();

        final IndexShard idx1 = createShard(index, volume);

        // Clean up from previous tests.
        final Path dir = IndexShardUtil.getIndexPath(idx1, pathCreator);
        FileUtil.deleteDir(dir);

        for (int i = 1; i <= 10; i++) {
            final IndexShardWriter writer = new Lucene553IndexShardWriter(
                    null, new IndexConfig(), idx1, pathCreator,
                    MAX_DOCS);
            writer.flush();
            writer.addDocument(buildDocument(i));
            writer.flush();
            assertThat(writer.getDocumentCount()).isEqualTo(i);
            writer.close();
        }
    }

    @Test
    void testOpenCloseManyReadWrite() throws IOException {
        final LuceneIndexDoc index = LuceneIndexDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test")
                .build();

        final IndexVolume volume = IndexVolume
                .builder()
                .path(FileUtil.getCanonicalPath(Files.createTempDirectory("stroom")))
                .build();
        final IndexShard idx1 = createShard(index, volume);

        // Clean up from previous tests.
        final Path dir = IndexShardUtil.getIndexPath(idx1, pathCreator);
        FileUtil.deleteDir(dir);

        for (int i = 1; i <= 10; i++) {
            final IndexShardWriter writer = new Lucene553IndexShardWriter(
                    null, new IndexConfig(), idx1, pathCreator,
                    MAX_DOCS);
            writer.addDocument(buildDocument(i));
            writer.close();
            assertThat(writer.getDocumentCount()).isEqualTo(i);

            final IndexShardSearcher indexShardSearcher = new IndexShardSearcher(idx1, pathCreator);
            final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
            final IndexSearcher searcher = searcherManager.acquire();
            try {
                assertThat(searcher.getIndexReader().maxDoc()).isEqualTo(i);
            } finally {
                searcherManager.release(searcher);
            }
            indexShardSearcher.destroy();
        }
    }

    @Test
    void testShardCorruption() {
//        final Executor executor = Executors.newCachedThreadPool();
//
//        final Index index = new Index();
//        index.setName("Test");
//
//        final Volume volume = new Volume();
//        volume.setPath(getCurrentTestDir().getAbsolutePath());
//        final IndexShard idx1 = new IndexShard();
//        idx1.setIndex(index);
//        idx1.setPartition("all");
//        idx1.setId(1L);
//        idx1.setVolume(volume);
//        idx1.setIndexVersion(LuceneVersionUtil.getCurrentVersion());
//
//        final IndexShardKey indexShardKey = IndexShardKey.createTestKey(index);
//
//        // Clean up from previous tests.
//        final File dir = IndexShardUtil.getIndexDir(idx1);
//        FileUtil.deleteDir(dir);
//
//        final Map<IndexShardKey, IndexShardWriter> map = new ConcurrentHashMap<>();
//
//        final List<CompletableFuture> futures = new ArrayList<>();
//
//        final CompletableFuture writerFuture = CompletableFuture.runAsync(() -> {
//            for (int i = 1; i < 1000000; i++) {
//                try {
//                    final IndexShardWriter writer = map.compute(indexShardKey, (k, v) -> {
//                        if (v != null) {
//                            return v;
//                        }
//
//                        IndexShardWriter result = null;
//                        try {
//                            result = new IndexShardWriterImpl(null, INDEX_CONFIG, indexShardKey, idx1);
//                        } catch (final IOException e) {
//                            System.err.println(e.getMessage());
//                        }
//                        return result;
//                    });
//
//                    if (writer != null) {
//                        //                        System.out.println("Adding doc " + i);
//                        writer.addDocument(buildDocument(i));
//                        ThreadUtil.sleep(1);
//                    }
//                } catch (final AlreadyClosedException e) {
//                    System.out.println(e.getMessage());
//                } catch (final RuntimeException e) {
//                    System.err.println(e.getMessage());
//                }
//            }
//        }, executor);
//        futures.add(writerFuture);
//
//        final CompletableFuture closerFuture = CompletableFuture.runAsync(() -> {
//            while (!writerFuture.isDone()) {
//                try {
//                    map.compute(indexShardKey, (k, v) -> {
//                        if (v != null) {
//                            ThreadUtil.sleep(10);
//                            v.close();
//                        }
//                        return null;
//                    });
//                    ThreadUtil.sleep(100);
//                } catch (final RuntimeException e) {
//                    System.err.println(e.getMessage());
//                }
//            }
//        }, executor);
//        futures.add(closerFuture);

//        for (int i = 0; i < 100; i++) {
//            final CompletableFuture readerFuture = CompletableFuture.runAsync(() -> {
//                while (!writerFuture.isDone()) {
//                    try {
//                        final IndexShardWriter indexShardWriter = map.get(indexShardKey);
//                        IndexWriter writer = null;
//                        if (indexShardWriter != null) {
//                            writer = indexShardWriter.getWriter();
//                        }
//
//                        if (writer != null) {
//                            final IndexShardSearcher indexShardSearcher = new IndexShardSearcherImpl(idx1, writer);
//                            final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
//                            final IndexSearcher searcher = searcherManager.acquire();
//                            //                final Query query = NumericRangeQuery.newLongRange(
//                            "Id", 3L, 100L, true, true);
//
//                            final Query query = new TermQuery(new Term("Test", "Test"));
//
//                            final TotalHitCountCollector collector = new TotalHitCountCollector();
//                            searcher.search(query, collector);
////                        System.out.println(collector.getTotalHits());
//                            ThreadUtil.sleep(10);
//                        }
//                    } catch (final SearchException e) {
//                        System.out.println(e.getMessage());
//                    } catch (final RuntimeException e) {
//                        System.err.println(e.getMessage());
//                    }
//                }
//            }, executor);
//            futures.add(readerFuture);
//        }
//
//
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//
//        final IndexShardSearcher indexShardSearcher = new IndexShardSearcherImpl(idx1, writer1.getWriter());
//        final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
//        final IndexSearcher searcher = searcherManager.acquire();
////        try {
//            assertThat(searcher.getIndexReader().maxDoc()).isEqualTo(1);
////        } finally {
////            searcherManager.release(searcher);
////        }
//
//        writer1.close();
//
//        final IndexShardWriter writer2 = new IndexShardWriterImpl(null, INDEX_CONFIG, indexShardKey, idx1);
//        writer2.addDocument(buildDocument(2));
//

//
//            final IndexShardSearcher indexShardSearcher = new IndexShardSearcherImpl(idx1);
//            final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
//            final IndexSearcher searcher = searcherManager.acquire();
//            try {
//                assertThat(searcher.getIndexReader().maxDoc()).isEqualTo(i);
//            } finally {
//                searcherManager.release(searcher);
//            }
//            indexShardSearcher.destroy();

    }

    @Test
    void testFailToCloseAndReopen() throws IOException {
        final LuceneIndexDoc index = LuceneIndexDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test")
                .build();

        final IndexVolume volume = IndexVolume
                .builder()
                .path(FileUtil.getCanonicalPath(Files.createTempDirectory("stroom")))
                .build();
        final IndexShard idx1 = createShard(index, volume);

        // Clean up from previous tests.
        final Path dir = IndexShardUtil.getIndexPath(idx1, pathCreator);
        FileUtil.deleteDir(dir);

        final IndexShardWriter writer = new Lucene553IndexShardWriter(
                null, new IndexConfig(), idx1, pathCreator,
                MAX_DOCS);

        for (int i = 1; i <= 10; i++) {
            writer.addDocument(buildDocument(i));
            writer.flush();
            assertThat(writer.getDocumentCount()).isEqualTo(i);
        }

        writer.close();
    }

    @Test
    void testFailToCloseFlushAndReopen() throws IOException {
        final LuceneIndexDoc index = LuceneIndexDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test")
                .build();

        final IndexVolume volume = IndexVolume
                .builder()
                .path(FileUtil.getCanonicalPath(Files.createTempDirectory("stroom")))
                .build();
        final IndexShard idx1 = createShard(index, volume);

        // Clean up from previous tests.
        final Path dir = IndexShardUtil.getIndexPath(idx1, pathCreator);
        FileUtil.deleteDir(dir);

        final IndexShardWriter writer = new Lucene553IndexShardWriter(
                null, new IndexConfig(), idx1, pathCreator, MAX_DOCS);

        for (int i = 1; i <= 10; i++) {
            writer.addDocument(buildDocument(i));
            writer.flush();
            assertThat(writer.getDocumentCount()).as("No docs flushed ").isEqualTo(i);
        }

        writer.close();
    }

    @Test
    void testWriteLoadsNoFlush() throws IOException {
        final LuceneIndexDoc index = LuceneIndexDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test")
                .build();

        final Path testDir = Files.createTempDirectory("stroom");
        final IndexVolume volume = IndexVolume
                .builder()
                .path(FileUtil.getCanonicalPath(testDir))
                .build();
        final IndexShard idx1 = createShard(index, volume);

        final IndexShardWriter writer = new Lucene553IndexShardWriter(
                null, new IndexConfig(), idx1, pathCreator, MAX_DOCS);

        Long lastSize = null;

        final Set<Integer> flushSet = new HashSet<>();

        for (int i = 1; i <= 100; i++) {
            writer.addDocument(buildDocument(i));
//            writer.sync();
            // System.out.println(writer.getIndexWriter().ramSizeInBytes());

            final Long newSize = idx1.getFileSize();

            if (newSize != null) {
                if (lastSize != null) {
                    if (!lastSize.equals(newSize)) {
                        flushSet.add(i);
                    }
                }
                lastSize = newSize;
            }

        }
        // TODO - TO Fix
        // assertThat(flushSet.size()).as("Some flush happened before we expected it "
        // + flushSet).isEqualTo(0);

        writer.close();
        assertThat(flushSet.isEmpty()).as("Expected not to flush").isTrue();
        // assertThat( // flushSet.toString()).as("Expected to flush every 2048 docs...").isEqualTo("[2048,
        // 6144, 4096, 8192]");
    }

    private IndexShard createShard(final LuceneIndexDoc index,
                                   final IndexVolume volume) {
        return IndexShard
                .builder()
                .id(1L)
                .indexUuid(index.getUuid())
                .partition("all")
                .volume(volume)
                .indexVersion(LuceneVersionUtil.getCurrentVersion())
                .build();
    }

    // --------------------------------------------------------------------------------


    private static class RecordingCommitListener implements IndexShardCommitListener {

        private final List<String> events = new ArrayList<>();
        private List<Long> writtenRowIds = List.of();

        @Override
        public List<Long> beforeCommit() {
            events.add("before");
            return writtenRowIds;
        }

        @Override
        public void afterCommit(final List<Long> rowIds) {
            events.add("after " + rowIds);
        }

        @Override
        public void commitFailed() {
            events.add("failed");
        }
    }
}
